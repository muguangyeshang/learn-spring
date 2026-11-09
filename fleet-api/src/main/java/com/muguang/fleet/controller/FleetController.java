package com.muguang.fleet.controller;

import com.muguang.fleet.config.JwtAuth;
import com.muguang.fleet.model.ServiceEntity;
import com.muguang.fleet.repo.ServiceRepository;
import jakarta.validation.Valid;
import java.util.Map;
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

    public FleetController(ServiceRepository repo, JwtAuth jwt) {
        // 构造器注入：依赖由 Spring 容器提供（IoC，控制反转）
        this.repo = repo;
        this.jwt = jwt;
    }

    /** POST /api/login  ->  {token}   演示用固定账号 */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String user = body.getOrDefault("username", "");
        String pass = body.getOrDefault("password", "");
        if (!"admin".equals(user) || !"admin123".equals(pass)) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        return ResponseEntity.ok(Map.of("token", jwt.issue(user)));
    }

    @GetMapping("/services")
    public Iterable<ServiceEntity> list() {
        return repo.findAllByOrderByIdAsc();
    }

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
