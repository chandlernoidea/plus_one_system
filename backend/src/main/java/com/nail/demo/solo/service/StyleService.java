package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.util.List;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.dto.StyleResponse;
import com.nail.demo.solo.dto.StyleSaveRequest;
import com.nail.demo.solo.entity.Style;
import com.nail.demo.solo.repository.StyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 款式库。下架用 enabled=0，不物理删除——历史预约里的项目已存快照，但款式本身保留便于复用。
 */
@Service
@RequiredArgsConstructor
public class StyleService {

    private final StyleRepository styleRepository;

    @Transactional(readOnly = true)
    public List<StyleResponse> list(Integer enabled, String tag, String keyword) {
        List<Style> styles;
        if (StringUtils.hasText(keyword)) {
            styles = styleRepository.findByNameContainingOrderByIdDesc(keyword.trim());
        } else if (StringUtils.hasText(tag)) {
            styles = styleRepository.findByTagsContainingOrderByIdDesc(tag.trim());
        } else if (enabled != null) {
            styles = styleRepository.findByEnabledOrderByIdDesc(enabled);
        } else {
            styles = styleRepository.findAllByOrderByIdDesc();
        }
        return styles.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StyleResponse get(Long id) {
        return toResponse(requireStyle(id));
    }

    @Transactional
    public StyleResponse create(StyleSaveRequest request) {
        Style style = new Style();
        apply(style, request);
        return toResponse(styleRepository.save(style));
    }

    @Transactional
    public StyleResponse update(Long id, StyleSaveRequest request) {
        Style style = requireStyle(id);
        apply(style, request);
        return toResponse(styleRepository.save(style));
    }

    /** 下架：不删数据，避免历史单的款式快照失去参照。 */
    @Transactional
    public void disable(Long id) {
        Style style = requireStyle(id);
        style.setEnabled(0);
        styleRepository.save(style);
    }

    private void apply(Style style, StyleSaveRequest request) {
        style.setName(request.getName().trim());
        style.setCoverUrl(request.getCoverUrl());
        style.setDurationMin(request.getDurationMin() == null ? 60 : request.getDurationMin());
        style.setPrice(request.getPrice() == null ? BigDecimal.ZERO : request.getPrice());
        style.setMaterialJson(request.getMaterialJson());
        style.setTags(request.getTags());
        style.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
    }

    private Style requireStyle(Long id) {
        return styleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "款式不存在"));
    }

    private StyleResponse toResponse(Style style) {
        return StyleResponse.builder()
                .id(style.getId())
                .name(style.getName())
                .coverUrl(style.getCoverUrl())
                .durationMin(style.getDurationMin())
                .price(style.getPrice())
                .materialJson(style.getMaterialJson())
                .tags(style.getTags())
                .enabled(style.getEnabled())
                .build();
    }
}
