package com.nail.demo.solo.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.nail.demo.solo.entity.DepositFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 定金流水查询入口（按单、按时间、按月聚合）。
 */
public interface DepositFlowRepository extends JpaRepository<DepositFlow, Long> {

    List<DepositFlow> findByBookingIdOrderByHappenedAtAsc(Long bookingId);

    /** 批量取一列表的定金流水，避免排期时 N+1。 */
    List<DepositFlow> findByBookingIdIn(Collection<Long> bookingIds);

    List<DepositFlow> findByHappenedAtBetweenOrderByHappenedAtAsc(LocalDateTime from, LocalDateTime to);

    /** 月度按类型汇总：[type, sum(amount)]。 */
    @Query("""
            select f.type, coalesce(sum(f.amount), 0) from DepositFlow f
            where f.happenedAt >= :from and f.happenedAt < :to
            group by f.type
            """)
    List<Object[]> sumByType(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
