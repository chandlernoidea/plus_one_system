package com.nail.demo.solo.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.nail.demo.solo.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** 按时间窗查排期（日 / 周视图）。 */
    List<Booking> findByStartAtBetweenOrderByStartAtAsc(LocalDateTime from, LocalDateTime to);

    /** 按状态 + 时间窗查排期，冲突检测只关心占用时段的状态。 */
    List<Booking> findByStatusInAndStartAtBetweenOrderByStartAtAsc(
            Collection<String> statuses, LocalDateTime from, LocalDateTime to);

    /**
     * 冲突检测：与 [start, end) 有交集的占用单。用 startAt/endAt 两端判断，
     * 才能抓到「先开的单还没结束」这种跨时段情况。
     */
    @Query("""
            select b from Booking b
            where b.status in :statuses
              and b.startAt < :end
              and b.endAt > :start
            order by b.startAt asc
            """)
    List<Booking> findOverlapping(@Param("statuses") Collection<String> statuses,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /** 客户历史预约。 */
    List<Booking> findByClientIdOrderByStartAtDesc(Long clientId);

    /** 连单分组内的所有预约。 */
    List<Booking> findByGroupIdOrderByStartAtAsc(String groupId);
}
