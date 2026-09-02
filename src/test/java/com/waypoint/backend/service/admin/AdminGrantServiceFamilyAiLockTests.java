package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.PremiumSpecialGrantRequest;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminGrantServiceFamilyAiLockTests {
    private UserRepository userRepository;
    private SpecialPremiumGrantRepository grantRepository;
    private PlanRepository planRepository;
    private PlanService planService;
    private AdminAuditEventRepository auditEventRepository;
    private AdminGrantService service;
    private UUID userId;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        grantRepository = mock(SpecialPremiumGrantRepository.class);
        planRepository = mock(PlanRepository.class);
        planService = mock(PlanService.class);
        auditEventRepository = mock(AdminAuditEventRepository.class);
        service = new AdminGrantService(
                userRepository,
                grantRepository,
                planRepository,
                planService,
                auditEventRepository
        );

        userId = UUID.randomUUID();
        user = new UserEntity();
        user.setId(userId);
        user.setEmail("special-user@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(planRepository.findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL))
                .thenReturn(Optional.of(new PlanEntity()));
        when(grantRepository.saveAndFlush(any(SpecialPremiumGrantEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void grantTakesGlobalFamilyLockBeforeUserGrantLock() {
        when(grantRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        service.grantPremiumSpecial(
                userId,
                new PremiumSpecialGrantRequest(Instant.now().plusSeconds(3600), "Friends and family"),
                "admin"
        );

        InOrder order = inOrder(planRepository, grantRepository);
        order.verify(planRepository).findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL);
        order.verify(grantRepository).findByUserIdForUpdate(userId);
    }

    @Test
    void revokeTakesGlobalFamilyLockBeforeUserGrantLock() {
        SpecialPremiumGrantEntity grant = new SpecialPremiumGrantEntity();
        grant.setUser(user);
        grant.setActive(true);
        grant.setReason("Friends and family");
        grant.setGrantedBy("admin");
        grant.setGrantedAt(Instant.now());
        when(grantRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(grant));

        service.revokePremiumSpecial(userId, "admin");

        InOrder order = inOrder(planRepository, grantRepository);
        order.verify(planRepository).findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL);
        order.verify(grantRepository).findByUserIdForUpdate(userId);
    }
}
