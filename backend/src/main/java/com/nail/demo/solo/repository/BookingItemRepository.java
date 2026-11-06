package com.nail.demo.solo.repository;

import java.util.Collection;
import java.util.List;

import com.nail.demo.solo.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 预约项目的查询入口。
 */
public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    List<BookingItem> findByBookingIdOrderBySeqAsc(Long bookingId);

    List<BookingItem> findByBookingIdInOrderBySeqAsc(Collection<Long> bookingIds);

    void deleteByBookingId(Long bookingId);
}
