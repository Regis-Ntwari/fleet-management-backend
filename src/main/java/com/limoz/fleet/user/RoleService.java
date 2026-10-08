package com.limoz.fleet.user;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.user.dto.PermissionResponse;
import com.limoz.fleet.user.dto.RoleRequest;
import com.limoz.fleet.user.dto.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'roles'")
    public List<RoleResponse> listRoles() {
        return roleRepository.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'permissions'")
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Role " + request.code() + " already exists");
        }
        Role role = new Role();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setSystemRole(false);
        role.setPermissions(resolvePermissions(request.permissions()));
        RoleResponse response = mapper.toResponse(roleRepository.save(role));
        auditService.record(AuditAction.CREATE, "Role", role.getId(), role.getCode(), null, response, "Role created");
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = roleRepository.findWithPermissionsById(id).orElseThrow(() -> new ResourceNotFoundException("Role", id));
        RoleResponse before = mapper.toResponse(role);
        if (!role.getCode().equals(request.code())) {
            if (role.isSystemRole()) {
                throw new BusinessRuleException("The code of a system role cannot be changed");
            }
            if (roleRepository.existsByCode(request.code())) {
                throw new DuplicateResourceException("Role " + request.code() + " already exists");
            }
            role.setCode(request.code());
        }
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(resolvePermissions(request.permissions()));
        RoleResponse after = mapper.toResponse(roleRepository.save(role));
        // permissions are embedded in tokens: force re-issue for affected users
        userRepository.findActiveByRoleCodes(Set.of(role.getCode())).forEach(u -> u.setTokenVersion(u.getTokenVersion() + 1));
        auditService.record(AuditAction.UPDATE, "Role", role.getId(), role.getCode(), before, after, "Role permissions updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public void delete(Long id) {
        Role role = roleRepository.findWithPermissionsById(id).orElseThrow(() -> new ResourceNotFoundException("Role", id));
        if (role.isSystemRole()) {
            throw new BusinessRuleException("System roles cannot be deleted");
        }
        if (!userRepository.findActiveByRoleCodes(Set.of(role.getCode())).isEmpty()) {
            throw new BusinessRuleException("Role is assigned to active users and cannot be deleted");
        }
        roleRepository.delete(role);
        auditService.record(AuditAction.DELETE, "Role", role.getId(), role.getCode(), mapper.toResponse(role), null, "Role deleted");
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        List<Permission> found = permissionRepository.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            throw new ResourceNotFoundException("One or more permission codes do not exist");
        }
        return new HashSet<>(found);
    }
}
