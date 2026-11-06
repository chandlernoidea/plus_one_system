package com.nail.demo.solo.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 客户档案。客户不是账号，标识 = 短号 + 昵称 + 手机后 4 位（选填）。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "solo_client")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 短号，如 #C001，自动生成且永不变更。唯一约束在 schema_solo.sql 的 uk_solo_client_code 上。 */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "phone_tail", length = 4)
    private String phoneTail;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "remark", length = 500)
    private String remark;

    /** 常用色号 / 偏好色，客户档案里手记，例：#E03 裸粉。 */
    @Column(name = "color_notes", length = 200)
    private String colorNotes;

    @Column(name = "first_visit_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate firstVisitDate;

    @Column(name = "last_visit_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastVisitDate;

    /** 软删除：删除客户后历史预约保留并标记「客户已删除」。 */
    @Column(name = "deleted", nullable = false)
    private Integer deleted = 0;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (deleted == null) {
            deleted = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
