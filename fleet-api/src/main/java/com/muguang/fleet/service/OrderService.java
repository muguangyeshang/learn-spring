package com.muguang.fleet.service;

import com.muguang.fleet.model.OrderEntity;
import com.muguang.fleet.repo.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接单业务规则：限流、去重、状态机。
 *
 * 为什么单独一层：控制器只管 HTTP 语义（状态码、JSON），
 * 业务判断放在这里，才能单独写单元测试而不起 Web 容器。
 *
 * 限流是进程内固定窗口，单实例够用；多实例部署要换成 Redis。
 * 复杂度：每次提交 O(1) 的 map 操作 + 一次带索引的查询。
 */
@Service
public class OrderService {

    /** 固定窗口计数器：key -> (窗口起点, 已用次数) */
    private static final class Window {
        final LocalDateTime start;
        final AtomicInteger used = new AtomicInteger(1);
        Window(LocalDateTime start) { this.start = start; }
    }

    private final OrderRepository repo;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int rateLimit;
    private final long dedupeSeconds;

    public OrderService(OrderRepository repo,
                        @Value("${fleet.order.rate-limit:20}") int rateLimit,
                        @Value("${fleet.order.dedupe-seconds:600}") long dedupeSeconds) {
        this.repo = repo;
        this.rateLimit = rateLimit;
        this.dedupeSeconds = dedupeSeconds;
    }

    /** 业务拒绝原因，控制器按 kind 映射成 429 / 409 */
    public static class Rejected extends RuntimeException {
        public final String kind;
        Rejected(String kind, String message) {
            super(message);
            this.kind = kind;
        }
    }

    @Transactional
    public OrderEntity create(OrderEntity incoming, String clientKey) {
        checkRate(clientKey);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusSeconds(dedupeSeconds);
        repo.findTop1ByPhoneAndServiceAndCreatedAtAfterOrderByCreatedAtDesc(
                        incoming.getPhone(), incoming.getService(), since)
                .ifPresent(old -> {
                    throw new Rejected("duplicate",
                            "同一手机号在 " + dedupeSeconds + " 秒内已提交过同一项目（单号 " + old.getId() + "）");
                });

        // 服务端只认这几个字段，客户端传来的 id/status/createdAt 一律不采信
        OrderEntity order = new OrderEntity();
        order.setCustomerName(incoming.getCustomerName());
        order.setPhone(incoming.getPhone());
        order.setService(incoming.getService());
        order.setPreferredAt(incoming.getPreferredAt());
        order.setNote(incoming.getNote());
        order.setStatus(OrderEntity.NEW);
        order.setSource(incoming.getSource());
        order.setCreatedAt(now);
        return repo.save(order);
    }

    /** 状态机：只允许 NEW->ACCEPTED->DONE，或任意态->CANCELLED */
    @Transactional
    public OrderEntity changeStatus(Long id, String next) {
        if (!next.equals(OrderEntity.ACCEPTED) && !next.equals(OrderEntity.DONE)
                && !next.equals(OrderEntity.CANCELLED)) {
            throw new Rejected("bad-status", "目标状态必须是 ACCEPTED / DONE / CANCELLED");
        }
        OrderEntity order = repo.findById(id)
                .orElseThrow(() -> new Rejected("not-found", "单号 " + id + " 不存在"));
        String cur = order.getStatus();
        boolean legal = next.equals(OrderEntity.CANCELLED)
                || (cur.equals(OrderEntity.NEW) && next.equals(OrderEntity.ACCEPTED))
                || (cur.equals(OrderEntity.ACCEPTED) && next.equals(OrderEntity.DONE));
        if (!legal) {
            throw new Rejected("bad-transition", "不允许 " + cur + " -> " + next);
        }
        order.setStatus(next);
        return repo.save(order);
    }

    private void checkRate(String clientKey) {
        LocalDateTime now = LocalDateTime.now();
        // compute 是原子操作，多请求并发时不会各自读到旧计数
        Window w = windows.compute(clientKey, (k, old) -> {
            if (old == null || old.start.isBefore(now.minusMinutes(1))) {
                return new Window(now);
            }
            old.used.incrementAndGet();
            return old;
        });
        if (w.used.get() > rateLimit) {
            throw new Rejected("rate-limited", "提交过于频繁，每分钟最多 " + rateLimit + " 单");
        }
        // 防止无界增长：窗口过期后清理（此处 O(n)，键数量受来源数约束，很小）
        if (windows.size() > 1024) {
            windows.entrySet().removeIf(e -> e.getValue().start.isBefore(now.minusMinutes(2)));
        }
    }

    /** 后台概览：一次遍历按状态计数（O(n)，n = 订单总数） */
    public Map<String, Long> stats() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        out.put(OrderEntity.NEW, 0L);
        out.put(OrderEntity.ACCEPTED, 0L);
        out.put(OrderEntity.DONE, 0L);
        out.put(OrderEntity.CANCELLED, 0L);
        List<OrderEntity> all = repo.findAll();
        for (OrderEntity o : all) {
            out.merge(o.getStatus(), 1L, Long::sum);
        }
        out.put("TOTAL", (long) all.size());
        return out;
    }
}
