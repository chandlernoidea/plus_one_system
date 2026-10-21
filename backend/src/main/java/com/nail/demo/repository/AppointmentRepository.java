package com.nail.demo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.nail.demo.entity.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    long countByStaffIdAndRequestedStartAndStatus(
            Long staffId, LocalDateTime requestedStart, String status);

    List<Appointment> findByStaffIdAndStatusInAndConfirmedStartLessThanAndConfirmedStartGreaterThan(
            Long staffId, List<String> statuses, LocalDateTime rangeEnd, LocalDateTime rangeStart);

    List<Appointment> findByStaffIdAndStatusInAndConfirmedStartBetween(
            Long staffId, List<String> statuses, LocalDateTime from, LocalDateTime to);

    List<Appointment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Appointment> findByStaffIdOrderByRequestedStartDesc(Long staffId);

    List<Appointment> findByStaffIdAndStatusOrderByRequestedStartAsc(Long staffId, String status);

    List<Appointment> findByStaffIdOrderByRequestedStartAsc(Long staffId);

    List<Appointment> findAllByOrderByRequestedStartAsc();

    List<Appointment> findByStatusOrderByRequestedStartAsc(String status);

    List<Appointment> findByStatusIn(List<String> statuses);

    List<Appointment> findByConfirmedStartBetweenAndStatusInOrderByConfirmedStartAsc(
            LocalDateTime from, LocalDateTime to, List<String> statuses);

    List<Appointment> findByStatusAndExpireAtBefore(String status, LocalDateTime time);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Appointment a set a.status = :expiredStatus, a.updatedAt = :now "
            + "where a.status = :pendingStatus and a.expireAt < :now")
    int expirePending(@Param("pendingStatus") String pendingStatus,
                      @Param("expiredStatus") String expiredStatus,
                      @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Appointment a set a.status = :expiredStatus, a.updatedAt = :now "
            + "where a.id = :id and a.status = :pendingStatus")
    int expireById(@Param("id") Long id,
                   @Param("pendingStatus") String pendingStatus,
                   @Param("expiredStatus") String expiredStatus,
                   @Param("now") LocalDateTime now);
}
