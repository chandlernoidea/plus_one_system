package com.nail.demo.service;

import java.util.List;

import com.nail.demo.dto.ServiceTypeResponse;
import com.nail.demo.entity.ServiceType;
import com.nail.demo.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceTypeService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ServiceTypeRepository serviceTypeRepository;

    public List<ServiceTypeResponse> listActive() {
        return serviceTypeRepository.findByStatusOrderByIdAsc(STATUS_ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    private ServiceTypeResponse toResponse(ServiceType serviceType) {
        return ServiceTypeResponse.builder()
                .id(serviceType.getId())
                .name(serviceType.getName())
                .estDurationMin(serviceType.getEstDurationMin())
                .estDurationMax(serviceType.getEstDurationMax())
                .bufferMinutes(serviceType.getBufferMinutes())
                .description(serviceType.getDescription())
                .build();
    }
}
