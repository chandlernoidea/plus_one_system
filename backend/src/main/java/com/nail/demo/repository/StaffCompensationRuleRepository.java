package com.nail.demo.repository;

import java.util.List;
import java.util.Optional;

import com.nail.demo.entity.StaffCompensationRule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffCompensationRuleRepository extends JpaRepository<StaffCompensationRule, Long> {

    Optional<StaffCompensationRule> findByStaffIdAndStatus(Long staffId, String status);

    Optional<StaffCompensationRule> findByStaffId(Long staffId);

    List<StaffCompensationRule> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StaffCompensationRule r where r.id = :id")
    Optional<StaffCompensationRule> findByIdForUpdate(@Param("id") Long id);
}
