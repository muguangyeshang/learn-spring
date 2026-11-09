package com.muguang.fleet.controller;

import com.muguang.fleet.model.OrderEntity;
import com.muguang.fleet.repo.OrderRepository;
import com.muguang.fleet.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接单 demo 的 REST 接口。表单页与后台数据页共用这一组路由。
 *
 * 所有路由都在 JwtAuth 过滤器之后：小程序先 POST /api/login 拿 token，
 * 再带 Bearer 访问这里的每一个接口。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;
    private final OrderRepository repo;

    public OrderController(OrderService service, OrderRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    /** POST /api/orders -> 创建预约单（去重、限流、服务端定状态） */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody OrderEntity incoming,
                                    HttpServletRequest req) {
        try {
            OrderEntity saved = service.create(incoming, clientKey(req));
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (OrderService.Rejected e) {
            HttpStatus code = switch (e.kind) {
                case "rate-limited" -> HttpStatus.TOO_MANY_REQUESTS; // 429
                case "duplicate" -> HttpStatus.CONFLICT;              // 409
                default -> HttpStatus.BAD_REQUEST;
            };
            return ResponseEntity.status(code).body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /api/orders?status=NEW&source=miniapp -> 后台列表 */
    @GetMapping
    public List<OrderEntity> list(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) String source) {
        if (status != null && !status.isBlank()) return repo.findByStatusOrderByCreatedAtDesc(status);
        if (source != null && !source.isBlank()) return repo.findBySourceOrderByCreatedAtDesc(source);
        return repo.findAllByOrderByCreatedAtDesc();
    }

    /** GET /api/orders/stats -> 各状态计数 */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return service.stats();
    }

    /** PATCH /api/orders/{id}/status -> 接单 / 完成 / 取消 */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        String next = body.getOrDefault("status", "");
        try {
            return ResponseEntity.ok(service.changeStatus(id, next));
        } catch (OrderService.Rejected e) {
            HttpStatus code = switch (e.kind) {
                case "not-found" -> HttpStatus.NOT_FOUND;
                case "bad-transition", "bad-status" -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_REQUEST;
            };
            return ResponseEntity.status(code).body(Map.of("error", e.getMessage()));
        }
    }

    /** 限流键：优先取反向代理传来的真实 IP，否则用远端地址 */
    private static String clientKey(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = req.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
