package com.limoz.fleet.user.mapper;

import com.limoz.fleet.user.domain.Permission;
import com.limoz.fleet.user.domain.Role;
import com.limoz.fleet.user.domain.User;

import com.limoz.fleet.user.dto.PermissionResponse;
import com.limoz.fleet.user.dto.RoleResponse;
import com.limoz.fleet.user.dto.UserResponse;
import com.limoz.fleet.user.dto.UserSummary;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getFullName(),
                user.getEmail(), user.getPhone(), user.getUsername(), user.isActive(), user.isMustChangePassword(),
                user.getLastLoginAt(), user.getDriverId(), user.roleCodes(), user.permissionCodes(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    public UserSummary toSummary(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystemRole(),
                role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet()));
    }

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getCode(), permission.getModule(), permission.getDescription());
    }
}
