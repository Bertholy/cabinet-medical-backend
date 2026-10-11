package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.PermissionRole;
import com.cabinet.cabinet.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRoleRepository extends JpaRepository<PermissionRole, Long> {
    List<PermissionRole> findByRole(Role role);

    Optional<PermissionRole> findByRoleAndCode(Role role, String code);
}
