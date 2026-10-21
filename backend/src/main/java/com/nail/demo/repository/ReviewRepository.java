package com.nail.demo.repository;

import java.util.List;
import java.util.Optional;

import com.nail.demo.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    List<Review> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Review> findByStaffIdOrderByCreatedAtDesc(Long staffId);

    List<Review> findAllByOrderByCreatedAtDesc();
}
