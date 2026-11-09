package com.muguang.fleet.repo;

import com.muguang.fleet.model.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA：方法名即查询，无需写 SQL。
 * 派生查询对参数做绑定，不拼接字符串——从根上避开 SQL 注入。
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findAllByOrderByCreatedAtDesc();

    List<OrderEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<OrderEntity> findBySourceOrderByCreatedAtDesc(String source);

    /** 去重窗口：同手机号 + 同项目，在 since 之后已有单则拒绝 */
    Optional<OrderEntity> findTop1ByPhoneAndServiceAndCreatedAtAfterOrderByCreatedAtDesc(
            String phone, String service, LocalDateTime since);
}
