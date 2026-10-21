package com.nail.demo.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.nail.demo.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    List<TimeSlot> findByStaffIdAndSlotStartBetweenOrderBySlotStartAsc(
            Long staffId, LocalDateTime from, LocalDateTime to);

    List<TimeSlot> findByStaffIdAndSlotStartGreaterThanEqualAndSlotStartLessThanOrderBySlotStartAsc(
            Long staffId, LocalDateTime from, LocalDateTime to);

    void deleteByStaffIdAndSlotStartBetween(Long staffId, LocalDateTime from, LocalDateTime to);

    boolean existsByStaffIdAndSlotStart(Long staffId, LocalDateTime slotStart);
}
