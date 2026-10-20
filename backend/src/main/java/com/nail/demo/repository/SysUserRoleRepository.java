package com.nail.demo.repository;

import java.util.List;

import com.nail.demo.entity.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {

    List<SysUserRole> findByUserId(Long userId);
}
