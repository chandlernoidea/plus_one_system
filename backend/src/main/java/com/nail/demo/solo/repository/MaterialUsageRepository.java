package com.nail.demo.solo.repository;

import java.util.List;

import com.nail.demo.solo.entity.MaterialUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialUsageRepository extends JpaRepository<MaterialUsage, Long> {

    /** 一单领用了哪些料。 */
    List<MaterialUsage> findByBookingId(Long bookingId);

    /** 某个料被哪些单领用过。 */
    List<MaterialUsage> findByMaterialId(Long materialId);
}
