package com.nail.demo.solo.repository;

import java.time.LocalDate;
import java.util.List;

import com.nail.demo.solo.entity.SlotBlock;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 营业安排查询入口。
 */
public interface SlotBlockRepository extends JpaRepository<SlotBlock, Long> {

    List<SlotBlock> findByWorkDateOrderByStartTimeAsc(LocalDate workDate);

    List<SlotBlock> findByWorkDateBetweenOrderByWorkDateAscStartTimeAsc(LocalDate from, LocalDate to);
}
