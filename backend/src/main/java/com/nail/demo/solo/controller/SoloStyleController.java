package com.nail.demo.solo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.StyleResponse;
import com.nail.demo.solo.dto.StyleSaveRequest;
import com.nail.demo.solo.service.StyleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 款式库接口：列表（可按标签/启用筛选）、新建、编辑、下架。
 */
@RestController
@RequestMapping("/api/solo/styles")
@RequiredArgsConstructor
public class SoloStyleController {

    private final StyleService styleService;

    @GetMapping
    public ApiResponse<List<StyleResponse>> list(@RequestParam(required = false) Integer enabled,
                                                 @RequestParam(required = false) String tag,
                                                 @RequestParam(required = false) String keyword) {
        return ApiResponse.success(styleService.list(enabled, tag, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<StyleResponse> get(@PathVariable Long id) {
        return ApiResponse.success(styleService.get(id));
    }

    @PostMapping
    public ApiResponse<StyleResponse> create(@Valid @RequestBody StyleSaveRequest request) {
        return ApiResponse.success(styleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StyleResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody StyleSaveRequest request) {
        return ApiResponse.success(styleService.update(id, request));
    }

    /** 下架而非物理删除，历史单的款式快照仍需可参照。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        styleService.disable(id);
        return ApiResponse.success();
    }
}
