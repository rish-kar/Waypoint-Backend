package com.waypoint.backend.service.plan;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlanService {
    private final PlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public PlanService(
            PlanRepository planRepository,
            SubscriptionService subscriptionService,
            UserRepository userRepository
    ) {
        this.planRepository = planRepository;
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PlanEntity require(PlanCode code) {
        return planRepository.findById(code)
                .orElseThrow(() -> new IllegalStateException("Required plan is missing: " + code));
    }

    @Transactional(readOnly = true)
    public PlanEntity effectivePlan(UUID userId) {
        return require(subscriptionService.current(userId).planCode());
    }

    @Transactional
    public PlanEntity synchronizeUserPlan(UserEntity user) {
        return synchronizeUserPlan(user, subscriptionService.current(user.getId()));
    }

    @Transactional
    public PlanEntity synchronizeUserPlan(UserEntity user, SubscriptionSnapshot snapshot) {
        PlanCode targetCode = snapshot.planCode();

        if (user.getPlan() != null && user.getPlan().getCode() == targetCode) {
            return user.getPlan();
        }

        PlanEntity targetPlan = require(targetCode);
        user.setPlan(targetPlan);
        userRepository.save(user);
        return targetPlan;
    }
}
