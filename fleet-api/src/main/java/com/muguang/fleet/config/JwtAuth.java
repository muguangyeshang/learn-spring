package com.muguang.fleet.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 的签发与校验，做成一个 Servlet 过滤器：
 * 登录接口签发 token；除登录外的所有请求必须带
 * Authorization: Bearer <token>，校验失败一律 401。
 *
 * 这是「中间件」概念的 Java 形态——对应 Python 的装饰器、
 * Go 的 http.Handler 包装、Node 的中间件函数。
 */
@Component
public class JwtAuth extends OncePerRequestFilter {

    private final SecretKey key;

    public JwtAuth(@Value("${fleet.jwt-secret}") String secret) {
        // HMAC-SHA256 至少 256 位密钥（32 字节）；生产环境从环境变量注入
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 token：subject 放用户名，24 小时有效 */
    public String issue(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(86400)))
                .signWith(key)
                .compact();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();

        // 登录与健康检查放行，其余都要票。
        // CORS 预检（OPTIONS）也放行：预检不带 Authorization 头是浏览器
        // 规范行为，把它拦了跨源前端就永远连不上——"前端突然连不上
        // 后端"的标准答案就是这一行。
        if ("OPTIONS".equals(req.getMethod())
                || path.equals("/api/login")
                || path.startsWith("/h2-console")) {
            chain.doFilter(req, resp);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            resp.sendError(401, "缺少 Bearer token");
            return;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(header.substring(7))
                    .getPayload();
            req.setAttribute("user", claims.getSubject());
            chain.doFilter(req, resp);
        } catch (JwtException | IllegalArgumentException e) {
            resp.sendError(401, "token 无效或过期");
        }
    }
}
