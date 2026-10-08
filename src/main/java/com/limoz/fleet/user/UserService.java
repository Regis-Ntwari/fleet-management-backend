package com.limoz.fleet.user;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.security.UserStatusCache;
import com.limoz.fleet.user.dto.ChangePasswordRequest;
import com.limoz.fleet.user.dto.CreateUserRequest;
import com.limoz.fleet.user.dto.ResetPasswordRequest;
import com.limoz.fleet.user.dto.UpdateUserRequest;
import com.limoz.fleet.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;
    private final AuditService auditService;
    private final UserStatusCache userStatusCache;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, String role, Boolean active, Pageable pageable) {
        Specification<User> spec = Specifications.and(
                Specifications.likeAny(query, "firstName", "lastName", "email", "username"),
                Specifications.equal("active", active),
                role == null ? null : (root, q, cb) -> cb.equal(root.join("roles").get("code"), role));
        return PageResponse.from(userRepository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with email " + request.email() + " already exists");
        }
        if (request.username() != null && userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("Username " + request.username() + " is already taken");
        }
        User user = new User();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(request.phone());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setMustChangePassword(request.mustChangePassword());
        user.setDriverId(request.driverId());
        user.setRoles(resolveRoles(request.roles()));
        user = userRepository.save(user);
        UserResponse response = mapper.toResponse(user);
        auditService.record(AuditAction.CREATE, "User", user.getId(), user.getEmail(), null, response, "User created");
        return response;
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = load(id);
        UserResponse before = mapper.toResponse(user);
        if (!user.getEmail().equalsIgnoreCase(request.email()) && userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with email " + request.email() + " already exists");
        }
        if (request.username() != null && !request.username().equalsIgnoreCase(user.getUsername())
                && userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("Username " + request.username() + " is already taken");
        }
        Set<String> newRoles = request.roles();
        boolean rolesChanged = !newRoles.equals(user.roleCodes());
        guardLastSuperAdmin(user, newRoles.contains(Roles.SUPER_ADMIN));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(request.phone());
        user.setUsername(request.username());
        user.setDriverId(request.driverId());
        user.setRoles(resolveRoles(newRoles));
        if (rolesChanged) {
            user.setTokenVersion(user.getTokenVersion() + 1);
            userStatusCache.evict(user.getId());
        }
        UserResponse after = mapper.toResponse(userRepository.save(user));
        auditService.record(rolesChanged ? AuditAction.ROLE_CHANGE : AuditAction.UPDATE, "User", user.getId(), user.getEmail(),
                before, after, rolesChanged ? "User roles changed" : "User updated");
        return after;
    }

    public UserResponse setActive(Long id, boolean active) {
        User user = load(id);
        if (!active) {
            guardLastSuperAdmin(user, false);
            if (SecurityUtils.currentUserId().map(id::equals).orElse(false)) {
                throw new BusinessRuleException("You cannot disable your own account");
            }
        }
        UserResponse before = mapper.toResponse(user);
        user.setActive(active);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userStatusCache.evict(user.getId());
        UserResponse after = mapper.toResponse(userRepository.save(user));
        auditService.record(active ? AuditAction.ENABLE : AuditAction.DISABLE, "User", user.getId(), user.getEmail(), before, after,
                active ? "User enabled" : "User disabled");
        return after;
    }

    public void resetPassword(Long id, ResetPasswordRequest request) {
        User user = load(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(request.mustChangePassword());
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userStatusCache.evict(user.getId());
        userRepository.save(user);
        auditService.record(AuditAction.PASSWORD_RESET, "User", user.getId(), user.getEmail(), null, null, "Password reset by administrator");
    }

    public void changeOwnPassword(Long userId, ChangePasswordRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("INVALID_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("SAME_PASSWORD", "New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userStatusCache.evict(user.getId());
        userRepository.save(user);
        auditService.record(AuditAction.PASSWORD_CHANGE, "User", user.getId(), user.getEmail(), null, null, "Password changed");
    }

    public User load(Long id) {
        return userRepository.findWithRolesById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        Set<Role> roles = new HashSet<>();
        for (String code : codes) {
            roles.add(roleRepository.findByCode(code)
                    .orElseThrow(() -> new ResourceNotFoundException("Role " + code + " does not exist")));
        }
        return roles;
    }

    private void guardLastSuperAdmin(User user, boolean keepsSuperAdmin) {
        if (user.roleCodes().contains(Roles.SUPER_ADMIN) && !keepsSuperAdmin) {
            long remaining = userRepository.findActiveByRoleCodes(Set.of(Roles.SUPER_ADMIN)).stream()
                    .filter(u -> !u.getId().equals(user.getId())).count();
            if (remaining == 0) {
                throw new BusinessRuleException("LAST_SUPER_ADMIN", "At least one active SUPER_ADMIN must remain");
            }
        }
    }
}
