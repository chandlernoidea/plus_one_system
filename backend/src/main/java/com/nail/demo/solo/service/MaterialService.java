package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloPurchaseStatus;
import com.nail.demo.solo.dto.MaterialResponse;
import com.nail.demo.solo.dto.MaterialSaveRequest;
import com.nail.demo.solo.dto.MaterialUsageRequest;
import com.nail.demo.solo.dto.MaterialUsageResponse;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.MaterialItem;
import com.nail.demo.solo.entity.MaterialUsage;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.MaterialItemRepository;
import com.nail.demo.solo.repository.MaterialUsageRepository;
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
