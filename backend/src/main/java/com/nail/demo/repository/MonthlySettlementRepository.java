package com.nail.demo.repository;

import java.util.List;
import java.util.Optional;

import com.nail.demo.entity.MonthlySettlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonthlySettlementRepository extends JpaRepository<MonthlySettlement, Long> {

    Optional<MonthlySettlement> findByStaffIdAndSettleMonth(Long staffId, String settleMonth);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MonthlySettlement s where s.staffId = :staffId and s.settleMonth = :settleMonth")
    Optional<MonthlySettlement> findByStaffIdAndSettleMonthForUpdate(
            @Param("staffId") Long staffId,
            @Param("settleMonth") String settleMonth);

    List<MonthlySettlement> findBySettleMonthOrderByStaffIdAsc(String settleMonth);

    List<MonthlySettlement> findByStaffIdOrderBySettleMonthDesc(Long staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MonthlySettlement s where s.id = :id")
    Optional<MonthlySettlement> findByIdForUpdate(@Param("id") Long id);
}
