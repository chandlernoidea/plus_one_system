package com.nail.demo.solo.repository;

import java.util.Collection;
import java.util.List;

import com.nail.demo.solo.entity.MaterialItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialItemRepository extends JpaRepository<MaterialItem, Long> {

    List<MaterialItem> findAllByOrderByNameAsc();

    List<MaterialItem> findByPurchaseStatusOrderByNameAsc(String purchaseStatus);

    List<MaterialItem> findByNameContainingOrderByNameAsc(String keyword);

    /** 作业单顶部的「待采购件数」：还没备好的料。 */
    long countByPurchaseStatusIn(Collection<String> purchaseStatuses);
}
