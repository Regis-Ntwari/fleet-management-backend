package com.limoz.fleet.settings.repository;

import com.limoz.fleet.settings.domain.SystemSetting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    Optional<SystemSetting> findByKey(String key);
    List<SystemSetting> findAllByOrderByCategoryAscKeyAsc();
}
