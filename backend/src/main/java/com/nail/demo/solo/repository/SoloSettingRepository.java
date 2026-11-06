package com.nail.demo.solo.repository;

import java.util.Optional;

import com.nail.demo.solo.entity.SoloSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoloSettingRepository extends JpaRepository<SoloSetting, Long> {

    Optional<SoloSetting> findBySettingKey(String settingKey);
}
