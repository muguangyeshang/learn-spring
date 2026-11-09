package com.muguang.fleet.controller;

import com.muguang.fleet.config.JwtAuth;
import com.muguang.fleet.model.ServiceEntity;
import com.muguang.fleet.repo.ServiceRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST 控制器：fleet API 的 Java 版。
 * 路由表与 fleetkeeper 面板的 /api/* 语义对齐，方便两个前端
 * （learn-web / learn-uniapp）以后直接切换数据源。
 *
 * 对比 Go：Go 是显式函数注册路由，Spring 是注解扫描——
 * 同一个意图，两种语言各自的惯用表达。
 */
@RestController
@RequestMapping("/api")
public class FleetController {

    private final ServiceRepository repo;
    private final JwtAuth jwt;
    private final String adminUser;
    private final String adminPassword;

    public FleetController(ServiceRepository repo, JwtAuth jwt,
                           @Value("${fleet.admin-user}") String adminUser,
                           @Value("${fleet.admin-password}") String adminPassword) {
        // 构造器注入：依赖由 Spring 容器提供（IoC，控制反转）
        this.repo = repo;
        this.jwt = jwt;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    /** POST /api/login  ->  {token}   账号由配置注入，不写死在源码里 */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String user = body.getOrDefault("username", "");
        String pass = body.getOrDefault("password", "");
        // 固定时长比较，避免按字符逐个短路造成的时序侧信道
        boolean userOk = java.security.MessageDigest.isEqual(
                user.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                adminUser.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean passOk = java.security.MessageDigest.isEqual(
                pass.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!userOk || !passOk) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        return ResponseEntity.ok(Map.of("token", jwt.issue(user)));
    }

    @GetMapping("/services")
    public Iterable<ServiceEntity> list() {
        return repo.findAllByOrderByIdAsc();
    }

    /**
     * POST /api/report  ->  fleetkeeper 各端把本机服务一览上报到云。
     * 全量覆盖式：同端机器（reporter 标识）的服务表整体替换，
     * 幂等——同一份上报连发两次结果一致。这是全家桶的第一条链路：
     * fleetkeeper(桌面/Go) -> Spring(云) -> Vue/UniApp(看板)。
     */
    @PostMapping("/report")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> report(@RequestBody Map<String, Object> body) {
        String reporter = String.valueOf(body.getOrDefault("reporter", "unknown"));
        Object servicesObj = body.get("services");
        if (!(servicesObj instanceof List<?> services)) {
            return ResponseEntity.badRequest().body(Map.of("error", "services 必须是数组"));
        }
        // 清掉该 reporter 旧数据（按 note 标记来源，字段里塞机器标识）
        repo.findAll().forEach(svc -> {
            if (reporter.equals(svc.getNote())) repo.delete(svc);
        });
        int accepted = 0;
        for (Object o : services) {
            if (!(o instanceof Map<?, ?> m)) continue;
            ServiceEntity svc = new ServiceEntity();
            svc.setName(truncate(str(m.get("name")), 64));
            Object port = m.get("port");
            Integer p = null;
            try { p = Integer.parseInt(String.valueOf(port)); } catch (Exception ignore) { }
            if (p == null || p < 1 || p > 65535) continue; // 坏行跳过不整单拒绝
            svc.setPort(p);
            Object lvl = m.get("level");
            try { svc.setLevel(Math.max(0, Math.min(4, Integer.parseInt(String.valueOf(lvl))))); }
            catch (Exception e) { svc.setLevel(0); }
            svc.setHeartbeat(truncate(str(m.get("heartbeat")), 32));
            svc.setNote(reporter);
            // 端口被其他机器占则更新而非新建（同端口全局唯一）
            repo.findByPort(p).ifPresent(old -> svc.setId(old.getId()));
            repo.save(svc);
            accepted++;
        }
        return ResponseEntity.ok(Map.of("reporter", reporter, "accepted", accepted));
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String truncate(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    @GetMapping("/services/{id}")
    public ResponseEntity<ServiceEntity> one(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/services")
    public ResponseEntity<?> create(@Valid @RequestBody ServiceEntity svc) {
        if (repo.findByPort(svc.getPort()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "端口 " + svc.getPort() + " 已存在"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(svc));
    }

    /** PATCH：部分更新——改哪个字段传哪个字段 */
    @PatchMapping("/services/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id, @RequestBody Map<String, Object> patch) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(svc -> {
                    if (patch.containsKey("name")) svc.setName((String) patch.get("name"));
                    if (patch.containsKey("level")) svc.setLevel((Integer) patch.get("level"));
                    if (patch.containsKey("heartbeat")) svc.setHeartbeat((String) patch.get("heartbeat"));
                    if (patch.containsKey("note")) svc.setNote((String) patch.get("note"));
                    if (patch.containsKey("paused")) svc.setPaused((Boolean) patch.get("paused"));
                    return ResponseEntity.ok(repo.save(svc));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}
