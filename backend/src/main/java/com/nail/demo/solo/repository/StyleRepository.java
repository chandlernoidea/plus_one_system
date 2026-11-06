package com.nail.demo.solo.repository;

import java.util.List;

import com.nail.demo.solo.entity.Style;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StyleRepository extends JpaRepository<Style, Long> {

    List<Style> findByEnabledOrderByIdDesc(Integer enabled);

    List<Style> findAllByOrderByIdDesc();

    /** 按标签筛选（标签以英文逗号拼接存储）。 */
    List<Style> findByTagsContainingOrderByIdDesc(String tag);

    List<Style> findByNameContainingOrderByIdDesc(String keyword);
}
