package com.nail.demo.solo.repository;

import java.util.Optional;

import com.nail.demo.solo.entity.SoloSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 设置键值查询入口。
 */
public interface SoloSettingRepository extends JpaRepository<SoloSetting, Long> {

    Optional<SoloSetting> findBySettingKey(String settingKey);
}
