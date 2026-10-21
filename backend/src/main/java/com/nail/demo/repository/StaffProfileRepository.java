package com.nail.demo.repository;

import java.util.List;
import java.util.Optional;

import com.nail.demo.entity.StaffProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    Optional<StaffProfile> findByUserId(Long userId);

    List<StaffProfile> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StaffProfile s where s.id = :id")
    Optional<StaffProfile> findByIdForUpdate(@Param("id") Long id);
}
