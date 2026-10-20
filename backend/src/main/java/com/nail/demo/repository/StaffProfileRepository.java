package com.nail.demo.repository;

import java.util.List;
import java.util.Optional;

import com.nail.demo.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    Optional<StaffProfile> findByUserId(Long userId);

    List<StaffProfile> findByStatus(String status);
}
