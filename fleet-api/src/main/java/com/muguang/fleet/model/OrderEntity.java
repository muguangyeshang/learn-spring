package com.muguang.fleet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 一条预约/接单记录。小程序表单页 POST 进来，后台数据页 GET 出去。
 *
 * 字段与 pages/book/book.vue 的表单逐一对应；status 由后台在
 * PATCH /api/orders/{id}/status 里推进，客户端不能自己指定。
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    /** 状态机：NEW -> ACCEPTED -> DONE，任意态 -> CANCELLED */
    public static final String NEW = "NEW";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DONE = "DONE";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "联系人不能为空")
    @Size(max = 64, message = "联系人最长 64 字")
    @Column(nullable = false, length = 64)
    private String customerName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不对")
    @Column(nullable = false, length = 32)
    private String phone;

    @NotBlank(message = "预约项目不能为空")
    @Size(max = 64, message = "项目名最长 64 字")
    @Column(nullable = false, length = 64)
    private String service;

    /** 期望上门/服务时间，ISO-8601 本地时间字符串，客户端直传 */
    @Size(max = 32)
    @Column(length = 32)
    private String preferredAt;

    @Size(max = 255, message = "备注最长 255 字")
    @Column(length = 255)
    private String note;

    @Column(nullable = false, length = 16)
    private String status = NEW;

    /** 来源渠道：miniapp / h5 / web，后台按来源筛选 */
    @Column(length = 32)
    private String source;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { this.customerName = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getService() { return service; }
    public void setService(String v) { this.service = v; }
    public String getPreferredAt() { return preferredAt; }
    public void setPreferredAt(String v) { this.preferredAt = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
