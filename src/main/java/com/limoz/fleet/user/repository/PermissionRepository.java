package com.limoz.fleet.user.repository;

import com.limoz.fleet.user.domain.Permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    List<Permission> findByCodeIn(java.util.Collection<String> codes);
}
