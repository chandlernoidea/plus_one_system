package com.nail.demo.solo.entity;

import java.math.BigDecimal;
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
 * 款式库，兼作品集。标准时长与标准价供建单时带出，材料清单为 JSON 字符串。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "solo_style")
public class Style {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    /** 标准时长（分钟），建单时可改，改动在预约为快照。 */
    @Column(name = "duration_min", nullable = false)
    private Integer durationMin = 60;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** 材料清单 JSON 字符串。 */
    @Column(name = "material_json", columnDefinition = "TEXT")
    private String materialJson;

    /** 标签，多个用英文逗号分隔，便于按标签筛选与导出案例图。 */
    @Column(name = "tags", length = 200)
    private String tags;

    @Column(name = "enabled", nullable = false)
    private Integer enabled = 1;

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
        if (durationMin == null) {
            durationMin = 60;
        }
        if (price == null) {
            price = BigDecimal.ZERO;
        }
        if (enabled == null) {
            enabled = 1;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
