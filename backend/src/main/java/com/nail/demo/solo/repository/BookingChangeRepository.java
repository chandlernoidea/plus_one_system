package com.nail.demo.solo.repository;

import java.util.List;

import com.nail.demo.solo.entity.BookingChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingChangeRepository extends JpaRepository<BookingChange, Long> {

    List<BookingChange> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    /** 待通知清单：只提醒老板。 */
    List<BookingChange> findByNotifiedOrderByCreatedAtDesc(Integer notified);
}
