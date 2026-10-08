package com.limoz.fleet.user.dto;

import java.util.Set;

public record RoleResponse(Long id, String code, String name, String description, boolean systemRole, Set<String> permissions) {}
