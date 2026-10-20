package com.nail.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTypeResponse {

    private Long id;
    private String name;
    private Integer estDurationMin;
    private Integer estDurationMax;
    private Integer bufferMinutes;
    private String description;
}
