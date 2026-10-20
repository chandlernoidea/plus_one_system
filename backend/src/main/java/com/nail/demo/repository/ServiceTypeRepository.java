package com.nail.demo.repository;

import java.util.List;

import com.nail.demo.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    List<ServiceType> findByStatusOrderByIdAsc(String status);
}
