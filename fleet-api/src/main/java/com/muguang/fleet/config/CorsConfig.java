package com.muguang.fleet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS：跨源浏览器请求的通行证。
 *
 * 为什么需要：浏览器对跨源请求先发一个 OPTIONS 预检，而 JWT 过滤器
 * 在它之前就把 OPTIONS 拦成 401/403 了——预检永远通不过。
 * 这就是"为什么后端加了过滤后前端突然连不上"的标准答案。
 *
 * 学习项目放行 localhost 的常见开发端口；生产环境应精确到域名。
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}
