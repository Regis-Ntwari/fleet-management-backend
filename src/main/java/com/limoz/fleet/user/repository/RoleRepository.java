package com.limoz.fleet.user.repository;

import com.limoz.fleet.user.domain.Role;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByCode(String code);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsById(Long id);

    boolean existsByCode(String code);
}
