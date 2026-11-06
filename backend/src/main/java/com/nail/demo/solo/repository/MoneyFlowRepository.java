package com.nail.demo.solo.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.nail.demo.solo.entity.MoneyFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyFlowRepository extends JpaRepository<MoneyFlow, Long> {

    List<MoneyFlow> findByHappenedAtBetweenOrderByHappenedAtAsc(LocalDateTime from, LocalDateTime to);

    List<MoneyFlow> findByDirectionAndHappenedAtBetweenOrderByHappenedAtAsc(
            String direction, LocalDateTime from, LocalDateTime to);

    /** 从明细跳回预约用。 */
    List<MoneyFlow> findByBookingId(Long bookingId);

    /** 月度聚合：与明细逐笔累加比对，用于对账。 */
    @Query("""
            select coalesce(sum(m.amount), 0) from MoneyFlow m
            where m.direction = :direction and m.happenedAt >= :from and m.happenedAt < :to
            """)
    BigDecimal sumByDirection(@Param("direction") String direction,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);
}
