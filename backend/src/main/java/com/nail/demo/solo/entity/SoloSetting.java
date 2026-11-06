package com.nail.demo.solo.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单人版设置：键值对，只存「改过的」，没存过就用代码里的默认值。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "solo_setting")
public class SoloSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 50)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 200)
    private String settingValue;

    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
}
