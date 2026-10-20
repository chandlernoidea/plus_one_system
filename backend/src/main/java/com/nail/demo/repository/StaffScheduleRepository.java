package com.nail.demo.repository;

import java.time.LocalDate;
import java.util.List;

import com.nail.demo.entity.StaffSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, Long> {

    List<StaffSchedule> findByStaffIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
            Long staffId, LocalDate from, LocalDate to);

    List<StaffSchedule> findByStaffIdAndWorkDateOrderByStartTimeAsc(Long staffId, LocalDate workDate);

    void deleteByStaffIdAndWorkDate(Long staffId, LocalDate workDate);
}
