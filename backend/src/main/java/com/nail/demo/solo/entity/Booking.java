package com.nail.demo.solo.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nail.demo.solo.common.SoloBookingSource;
import com.nail.demo.solo.common.SoloBookingStatus;
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
 * 预约单。
 *
 * <p>占用 = 各项目时长之和 + 缓冲；end_at 为冗余落库值，便于用一条 SQL 判断时段重叠。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "solo_booking")
public class Booking {

    /** 默认缓冲（分钟）。 */
    public static final int DEFAULT_BUFFER_MINUTES = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "start_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    /** 预计时长 = 各项目时长之和（不含缓冲）。 */
    @Column(name = "planned_minutes", nullable = false)
    private Integer plannedMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    private Integer bufferMinutes = DEFAULT_BUFFER_MINUTES;

    /** 结束时间 = startAt + plannedMinutes + bufferMinutes。 */
    @Column(name = "end_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    /** 连单分组：同一客户多项目、两位朋友先后到店共用同一 groupId。 */
    @Column(name = "group_id", length = 32)
    private String groupId;

    /** 免费改期是否已用：≥48h 且未用过免费改期时改一次。 */
    @Column(name = "free_reschedule_used", nullable = false)
    private Integer freeRescheduleUsed = 0;

    @Column(name = "remark", length = 500)
    private String remark;

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
        if (bufferMinutes == null) {
            bufferMinutes = DEFAULT_BUFFER_MINUTES;
        }
        if (freeRescheduleUsed == null) {
            freeRescheduleUsed = 0;
        }
        if (status == null) {
            status = SoloBookingStatus.LEAD;
        }
        if (source == null) {
            source = SoloBookingSource.NORMAL;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
