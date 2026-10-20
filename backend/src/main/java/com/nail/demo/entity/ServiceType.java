package com.nail.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "service_type")
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "est_duration_min", nullable = false)
    private Integer estDurationMin;

    @Column(name = "est_duration_max", nullable = false)
    private Integer estDurationMax;

    @Column(name = "buffer_minutes", nullable = false)
    private Integer bufferMinutes;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "status", length = 20)
    private String status;
}
