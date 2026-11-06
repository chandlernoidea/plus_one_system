package com.nail.demo.solo.repository;

import java.util.List;

import com.nail.demo.solo.entity.BookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 预约状态事件的查询入口。
 */
public interface BookingEventRepository extends JpaRepository<BookingEvent, Long> {

    List<BookingEvent> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
