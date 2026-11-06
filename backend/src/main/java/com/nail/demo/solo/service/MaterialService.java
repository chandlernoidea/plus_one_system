package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloBookingStatus;
import com.nail.demo.solo.common.SoloPurchaseStatus;
import com.nail.demo.solo.dto.MaterialNeedResponse;
import com.nail.demo.solo.dto.MaterialResponse;
import com.nail.demo.solo.dto.MaterialSaveRequest;
import com.nail.demo.solo.dto.MaterialUsageRequest;
import com.nail.demo.solo.dto.MaterialUsageResponse;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.BookingItem;
import com.nail.demo.solo.entity.MaterialItem;
import com.nail.demo.solo.entity.MaterialUsage;
import com.nail.demo.solo.entity.Style;
import com.nail.demo.solo.repository.BookingItemRepository;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.MaterialItemRepository;
import com.nail.demo.solo.repository.MaterialUsageRepository;
import com.nail.demo.solo.repository.StyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 物料库存与按单领用。领用会扣库存并写成本快照；赞助物料成本记 0。
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialItemRepository materialItemRepository;
    private final MaterialUsageRepository materialUsageRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final StyleRepository styleRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<MaterialResponse> list(String status, String keyword) {
        List<MaterialItem> items;
        if (StringUtils.hasText(keyword)) {
            items = materialItemRepository.findByNameContainingOrderByNameAsc(keyword.trim());
        } else if (StringUtils.hasText(status)) {
            items = materialItemRepository.findByPurchaseStatusOrderByNameAsc(normalizeStatus(status));
        } else {
            items = materialItemRepository.findAllByOrderByNameAsc();
        }
        return items.stream().map(this::toResponse).toList();
    }

    /**
     * 今天要买的料：当天所有预约的款式材料清单按名字聚合，再和库存对一遍。
     *
     * <p>缺口 = 需要 − 库存；库存里都没建过这个物料，也算缺。
     */
    @Transactional(readOnly = true)
    public List<MaterialNeedResponse> needsOfDay(LocalDate date) {
        List<Long> bookingIds = bookingRepository
                .findByStartAtBetweenOrderByStartAtAsc(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .filter(booking -> !SoloBookingStatus.CANCELLED.equals(booking.getStatus())
                        && !SoloBookingStatus.NO_SHOW.equals(booking.getStatus()))
                .map(Booking::getId)
                .toList();
        if (bookingIds.isEmpty()) {
            return List.of();
        }

        List<BookingItem> items = bookingItemRepository.findByBookingIdInOrderBySeqAsc(bookingIds);
        Map<Long, Style> styles = styleRepository.findAllById(items.stream()
                        .map(BookingItem::getStyleId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Style::getId, Function.identity()));

        Map<String, BigDecimal> need = new LinkedHashMap<>();
        for (BookingItem item : items) {
            Style style = item.getStyleId() == null ? null : styles.get(item.getStyleId());
            if (style == null || !StringUtils.hasText(style.getMaterialJson())) {
                continue;
            }
            for (MaterialLine line : parseMaterial(style.getMaterialJson())) {
                need.merge(line.name(), BigDecimal.valueOf(line.qty()), BigDecimal::add);
            }
        }
        if (need.isEmpty()) {
            return List.of();
        }

        Map<String, MaterialItem> inventory = materialItemRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(MaterialItem::getName, Function.identity(), (a, b) -> a));

        List<MaterialNeedResponse> result = new ArrayList<>();
        need.forEach((name, qty) -> {
            MaterialItem material = inventory.get(name);
            BigDecimal stock = material == null || material.getQuantity() == null
                    ? BigDecimal.ZERO : material.getQuantity();
            BigDecimal shortfall = qty.subtract(stock).max(BigDecimal.ZERO);
            boolean ready = material != null
                    && SoloPurchaseStatus.READY.equals(material.getPurchaseStatus())
                    && shortfall.signum() == 0;
            result.add(MaterialNeedResponse.builder()
                    .name(name)
                    .needQty(qty)
                    .materialId(material == null ? null : material.getId())
                    .unit(material == null ? null : material.getUnit())
                    .stockQty(stock)
                    .shortfall(shortfall)
                    .purchaseStatus(material == null ? null : material.getPurchaseStatus())
                    .sponsored(material == null ? null : material.getSponsored())
                    .ready(ready)
                    .build());
        });
        result.sort((a, b) -> b.getShortfall().compareTo(a.getShortfall()));
        return result;
    }

    /** 材料清单 JSON：[{name, qty}]；解析失败就当没有，不影响排期。 */
    private List<MaterialLine> parseMaterial(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<MaterialLine> lines = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String name = node.path("name").asText("").trim();
                    if (!name.isEmpty()) {
                        lines.add(new MaterialLine(name, node.path("qty").asDouble(1)));
                    }
                }
            }
            return lines;
        } catch (Exception e) {
            return List.of();
        }
    }

    private record MaterialLine(String name, double qty) {
    }

    @Transactional
    public MaterialResponse create(MaterialSaveRequest request) {
        MaterialItem item = new MaterialItem();
        apply(item, request);
        return toResponse(materialItemRepository.save(item));
    }

    @Transactional
    public MaterialResponse update(Long id, MaterialSaveRequest request) {
        MaterialItem item = requireMaterial(id);
        apply(item, request);
        return toResponse(materialItemRepository.save(item));
    }

    @Transactional
    public MaterialResponse updatePurchaseStatus(Long id, String status) {
        MaterialItem item = requireMaterial(id);
        item.setPurchaseStatus(normalizeStatus(status));
        return toResponse(materialItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<MaterialUsageResponse> usages(Long bookingId) {
        List<MaterialUsage> usages = materialUsageRepository.findByBookingId(bookingId);
        Map<Long, MaterialItem> materials = materialItemRepository.findAllById(
                        usages.stream().map(MaterialUsage::getMaterialId).distinct().toList())
                .stream().collect(Collectors.toMap(MaterialItem::getId, Function.identity()));
        return usages.stream().map(usage -> toUsageResponse(usage, materials.get(usage.getMaterialId())))
                .toList();
    }

    /**
     * 按单领用：扣库存 + 写成本快照（赞助物料为 0）。
     */
    @Transactional
    public MaterialUsageResponse addUsage(Long bookingId, MaterialUsageRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(404, "预约不存在"));
        MaterialItem material = requireMaterial(request.getMaterialId());

        BigDecimal quantity = request.getQuantity();
        if (material.getQuantity().compareTo(quantity) < 0) {
            throw new BusinessException("库存不足：%s 只剩 %s %s".formatted(
                    material.getName(),
                    material.getQuantity().stripTrailingZeros().toPlainString(),
                    material.getUnit()));
        }

        BigDecimal cost = Integer.valueOf(1).equals(material.getSponsored())
                ? BigDecimal.ZERO
                : material.getUnitCost().multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        material.setQuantity(material.getQuantity().subtract(quantity));
        materialItemRepository.save(material);

        MaterialUsage usage = new MaterialUsage();
        usage.setBookingId(booking.getId());
        usage.setMaterialId(material.getId());
        usage.setQuantity(quantity);
        usage.setCostSnapshot(cost);
        usage.setRemark(request.getRemark());
        MaterialUsage saved = materialUsageRepository.save(usage);
        return toUsageResponse(saved, material);
    }

    private void apply(MaterialItem item, MaterialSaveRequest request) {
        item.setName(request.getName().trim());
        item.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : "个");
        item.setUnitCost(request.getUnitCost() == null ? BigDecimal.ZERO : request.getUnitCost());
        item.setQuantity(request.getQuantity() == null ? BigDecimal.ZERO : request.getQuantity());
        item.setSponsored(request.getSponsored() == null ? 0 : request.getSponsored());
        item.setSponsorName(request.getSponsorName());
        item.setSponsorValue(request.getSponsorValue());
        item.setPurchaseStatus(StringUtils.hasText(request.getPurchaseStatus())
                ? normalizeStatus(request.getPurchaseStatus())
                : SoloPurchaseStatus.TODO);
        item.setRemark(request.getRemark());

        // 赞助物料成本记 0，赞助价值另外记。
        if (Integer.valueOf(1).equals(item.getSponsored())) {
            item.setUnitCost(BigDecimal.ZERO);
        }
    }

    private MaterialItem requireMaterial(Long id) {
        return materialItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "物料不存在"));
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case SoloPurchaseStatus.TODO, SoloPurchaseStatus.ORDERED, SoloPurchaseStatus.SHIPPING,
                 SoloPurchaseStatus.ARRIVED, SoloPurchaseStatus.READY -> normalized;
            default -> throw new BusinessException("采购状态只能是 TODO / ORDERED / SHIPPING / ARRIVED / READY");
        };
    }

    private MaterialResponse toResponse(MaterialItem item) {
        return MaterialResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .unit(item.getUnit())
                .unitCost(item.getUnitCost())
                .quantity(item.getQuantity())
                .sponsored(item.getSponsored())
                .sponsorName(item.getSponsorName())
                .sponsorValue(item.getSponsorValue())
                .purchaseStatus(item.getPurchaseStatus())
                .remark(item.getRemark())
                .build();
    }

    private MaterialUsageResponse toUsageResponse(MaterialUsage usage, MaterialItem material) {
        return MaterialUsageResponse.builder()
                .id(usage.getId())
                .bookingId(usage.getBookingId())
                .materialId(usage.getMaterialId())
                .materialName(material == null ? "物料已删除" : material.getName())
                .quantity(usage.getQuantity())
                .costSnapshot(usage.getCostSnapshot())
                .createdAt(usage.getCreatedAt())
                .build();
    }
}
