package com.waypoint.backend.service.admin;

import com.waypoint.backend.config.admin.AdminProperties;
import com.waypoint.backend.model.admin.AdminAccountCreateRequest;
import com.waypoint.backend.model.admin.AdminAccountEntity;
import com.waypoint.backend.model.admin.AdminAccountResponse;
import com.waypoint.backend.model.admin.AdminAccountUpdateRequest;
import com.waypoint.backend.model.admin.AdminAuditEventEntity;
import com.waypoint.backend.model.admin.AdminRole;
import com.waypoint.backend.repository.admin.AdminAccountRepository;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.security.admin.AdminTotpVerifier;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.NotFoundException;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminAccountService implements UserDetailsService {
    private final AdminAccountRepository adminAccountRepository;
    private final AdminAuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminTotpVerifier totpVerifier;

    public AdminAccountService(
            AdminAccountRepository adminAccountRepository,
            AdminAuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            AdminTotpVerifier totpVerifier
    ) {
        this.adminAccountRepository = adminAccountRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpVerifier = totpVerifier;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminAccountEntity account = adminAccountRepository.findByUsernameIgnoreCase(normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Admin account not found"));
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(account.getRole().name())
                .disabled(!account.isActive())
                .build();
    }

    @Transactional(readOnly = true)
    public boolean validTotp(String username, String code) {
        AdminAccountEntity account = adminAccountRepository.findByUsernameIgnoreCase(normalizeUsername(username))
                .filter(AdminAccountEntity::isActive)
                .orElse(null);
        if (account == null || !StringUtils.hasText(account.getTotpSecret())) {
            return false;
        }
        try {
            return totpVerifier.validCode(account.getTotpSecret(), code, java.time.Instant.now());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Transactional
    public void bootstrap(AdminProperties properties) {
        if (adminAccountRepository.count() > 0) {
            return;
        }
        AdminAccountEntity account = new AdminAccountEntity();
        account.setUsername(normalizeUsername(properties.id()));
        account.setPasswordHash(passwordEncoder.encode(properties.password()));
        account.setRole(AdminRole.SUPER_ADMIN);
        account.setTotpSecret(normalizeSecret(properties.totpSecret(), false));
        account.setActive(true);
        adminAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> list() {
        return adminAccountRepository.findAllByOrderByUsernameAsc().stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public AdminAccountResponse create(AdminAccountCreateRequest request, String actor) {
        String username = normalizeUsername(request.username());
        if (adminAccountRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new InvalidRequestException("Admin username already exists");
        }
        String secret = normalizeSecret(request.totpSecret(), true);
        AdminAccountEntity account = new AdminAccountEntity();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(request.role());
        account.setTotpSecret(secret);
        account.setActive(true);
        AdminAccountEntity saved = adminAccountRepository.save(account);
        audit(actor, "CREATE_ADMIN_ACCOUNT", saved.getId(), "role=" + saved.getRole());
        return AdminAccountResponse.from(saved);
    }

    @Transactional
    public AdminAccountResponse update(UUID accountId, AdminAccountUpdateRequest request, String actor) {
        if (request.role() == null
                && request.active() == null
                && !StringUtils.hasText(request.password())
                && !StringUtils.hasText(request.totpSecret())) {
            throw new InvalidRequestException("At least one admin account field must be supplied");
        }

        AdminAccountEntity account = adminAccountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Admin account not found"));
        boolean removesLastSuperAdmin = account.isActive()
                && account.getRole() == AdminRole.SUPER_ADMIN
                && ((request.active() != null && !request.active())
                    || (request.role() != null && request.role() != AdminRole.SUPER_ADMIN));
        if (removesLastSuperAdmin
                && adminAccountRepository.countByRoleAndActiveTrue(AdminRole.SUPER_ADMIN) <= 1) {
            throw new InvalidRequestException("At least one active SUPER_ADMIN must remain");
        }

        if (request.role() != null) {
            account.setRole(request.role());
        }
        if (request.active() != null) {
            account.setActive(request.active());
        }
        if (StringUtils.hasText(request.password())) {
            account.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (StringUtils.hasText(request.totpSecret())) {
            account.setTotpSecret(normalizeSecret(request.totpSecret(), true));
        }

        AdminAccountEntity saved = adminAccountRepository.save(account);
        audit(actor, "UPDATE_ADMIN_ACCOUNT", saved.getId(),
                "role=" + saved.getRole() + ", active=" + saved.isActive());
        return AdminAccountResponse.from(saved);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new InvalidRequestException("Admin username is required");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSecret(String secret, boolean required) {
        if (!StringUtils.hasText(secret)) {
            if (required) {
                throw new InvalidRequestException("Admin TOTP secret is required");
            }
            return null;
        }
        String normalized = secret.replace(" ", "").trim().toUpperCase(Locale.ROOT);
        try {
            totpVerifier.validateSecret(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(exception.getMessage());
        }
        return normalized;
    }

    private void audit(String actor, String action, UUID accountId, String details) {
        AdminAuditEventEntity event = new AdminAuditEventEntity();
        event.setAdminId(actor);
        event.setAction(action);
        event.setResourceType("ADMIN_ACCOUNT");
        event.setResourceId(accountId.toString());
        event.setDetails(details);
        auditEventRepository.save(event);
    }
}