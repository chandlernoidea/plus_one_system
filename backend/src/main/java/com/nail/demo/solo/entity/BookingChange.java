package com.nail.demo.solo.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nail.demo.solo.common.SoloChangeDepositAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 改期 / 顺延 / 插队记录。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "solo_booking_change")
public class BookingChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 原开始时间；新建插单时为空。 */
    @Column(name = "old_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime oldStart;

    @Column(name = "new_start", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime newStart;

    /** 发起方：CLIENT / OWNER。 */
    @Column(name = "initiator", nullable = false, length = 20)
    private String initiator;

    /** 发起时距原开始的小时数，改期免费与否据此判定。 */
    @Column(name = "hours_before", nullable = false)
    private Integer hoursBefore = 0;

    @Column(name = "deposit_action", nullable = false, length = 20)
    private String depositAction;

    @Column(name = "notified", nullable = false)
    private Integer notified = 0;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (hoursBefore == null) {
            hoursBefore = 0;
        }
        if (notified == null) {
            notified = 0;
        }
        if (depositAction == null) {
            depositAction = SoloChangeDepositAction.NONE;
        }
    }
}
