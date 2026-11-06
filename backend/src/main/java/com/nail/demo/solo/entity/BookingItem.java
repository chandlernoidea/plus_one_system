package com.nail.demo.solo.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预约项目。款式名称与单价做快照，款式改价不影响历史单。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "solo_booking_item")
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** 款式 ID；手写项目可为空。 */
    @Column(name = "style_id")
    private Long styleId;

    @Column(name = "style_name", nullable = false, length = 80)
    private String styleName;

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** 是否加项。 */
    @Column(name = "addon", nullable = false)
    private Integer addon = 0;

    /** 项目顺序，从 0 开始；决定连做时的先后。 */
    @Column(name = "seq", nullable = false)
    private Integer seq = 0;
}
