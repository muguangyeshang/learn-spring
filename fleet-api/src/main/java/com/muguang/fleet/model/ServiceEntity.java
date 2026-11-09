package com.muguang.fleet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 一台受管服务。对应 fleetkeeper fleet.json registry.services 里的一行。
 * JPA 注解把类映射到 SQLite 表；校验注解让坏数据在进门时就被拒。
 */
@Entity
@Table(name = "services")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "服务名不能为空")
    @Column(nullable = false, length = 64)
    private String name;

    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口必须是 1-65535")
    @Max(value = 65535, message = "端口必须是 1-65535")
    @Column(nullable = false, unique = true)
    private Integer port;

    /** 证据等级 0-4，L4 = 业务探针通过 */
    @Min(0) @Max(4)
    @Column(nullable = false)
    private Integer level = 0;

    @Column(length = 32)
    private String heartbeat;

    @Column(length = 255)
    private String note;

    /** 软删除：paused 的服务守护时跳过（fleetkeeper 的暂停门） */
    @Column(nullable = false)
    private boolean paused = false;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getHeartbeat() { return heartbeat; }
    public void setHeartbeat(String heartbeat) { this.heartbeat = heartbeat; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
}
