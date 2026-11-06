package com.nail.demo.solo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.ClientCreateRequest;
import com.nail.demo.solo.dto.ClientResponse;
import com.nail.demo.solo.dto.ClientUpdateRequest;
import com.nail.demo.solo.service.ClientService;
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

@RestController
@RequestMapping("/api/solo/clients")
@RequiredArgsConstructor
public class SoloClientController {

    private final ClientService clientService;

    /** keyword 支持短号 / 昵称 / 手机后四位，三选一命中即可。 */
    @GetMapping
    public ApiResponse<List<ClientResponse>> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(clientService.search(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<ClientResponse> get(@PathVariable Long id) {
        return ApiResponse.success(clientService.get(id));
    }

    @PostMapping
    public ApiResponse<ClientResponse> create(@Valid @RequestBody ClientCreateRequest request) {
        return ApiResponse.success(clientService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ClientResponse> update(@PathVariable Long id,
                                              @RequestBody ClientUpdateRequest request) {
        return ApiResponse.success(clientService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        clientService.delete(id);
        return ApiResponse.success();
    }
}
