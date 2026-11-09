package com.muguang.fleet.repo;

import com.muguang.fleet.model.ServiceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA 的核心卖点：接口方法名即 SQL。
 * findByName / findByPortNotBlankName... 框架按方法名生成查询，
 * 不写一行 SQL —— 这是和 Python/Go 手写查询最大的框架差异。
 */
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    Optional<ServiceEntity> findByPort(Integer port);

    List<ServiceEntity> findAllByOrderByIdAsc();

    List<ServiceEntity> findByPausedFalseOrderByIdAsc();
}
