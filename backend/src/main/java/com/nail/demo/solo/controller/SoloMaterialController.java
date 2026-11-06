package com.nail.demo.solo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.MaterialResponse;
import com.nail.demo.solo.dto.MaterialSaveRequest;
import com.nail.demo.solo.dto.PurchaseStatusRequest;
import com.nail.demo.solo.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solo/materials")
@RequiredArgsConstructor
public class SoloMaterialController {

    private final MaterialService materialService;

    @GetMapping
    public ApiResponse<List<MaterialResponse>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword) {
        return ApiResponse.success(materialService.list(status, keyword));
    }

    @PostMapping
    public ApiResponse<MaterialResponse> create(@Valid @RequestBody MaterialSaveRequest request) {
        return ApiResponse.success(materialService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MaterialResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody MaterialSaveRequest request) {
        return ApiResponse.success(materialService.update(id, request));
    }

    /** 采购状态流转：待采购 → 已下单 → 在途 → 已到货 → 已备好。 */
    @PostMapping("/{id}/purchase-status")
    public ApiResponse<MaterialResponse> updatePurchaseStatus(@PathVariable Long id,
                                                              @Valid @RequestBody PurchaseStatusRequest request) {
        return ApiResponse.success(materialService.updatePurchaseStatus(id, request.getStatus()));
    }
}
