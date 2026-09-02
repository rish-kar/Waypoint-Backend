package com.waypoint.backend.service.ai;

import com.waypoint.backend.config.ai.FamilyAiAccessProperties;
import com.waypoint.backend.model.ai.AiChatMessage;
import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.FamilyAiPoolUsageEntity;
import com.waypoint.backend.model.ai.FamilyAiUsageResponse;
import com.waypoint.backend.model.ai.FamilyAiUserUsageEntity;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.repository.ai.FamilyAiPoolUsageRepository;
import com.waypoint.backend.repository.ai.FamilyAiUserUsageRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.utilities.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class FamilyAiBudgetService {
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal SAFETY_MULTIPLIER = new BigDecimal("2.0");
    private static final long MICRORUPEES_PER_RUPEE = 1_000_000L;
    private static final int PROVIDER_OVERHEAD_TOKENS = 5_000;

    private final FamilyAiAccessProperties properties;
    private final SpecialPremiumGrantRepository grantRepository;
    private final PlanRepository planRepository;
    private final FamilyAiPoolUsageRepository poolRepository;
    private final FamilyAiUserUsageRepository userUsageRepository;

    public FamilyAiBudgetService(
            FamilyAiAccessProperties properties,
            SpecialPremiumGrantRepository grantRepository,
            PlanRepository planRepository,
            FamilyAiPoolUsageRepository poolRepository,
            FamilyAiUserUsageRepository userUsageRepository
    ) {
        this.properties = properties;
        this.grantRepository = grantRepository;
        this.planRepository = planRepository;
        this.poolRepository = poolRepository;
        this.userUsageRepository = userUsageRepository;
    }

    public int estimateInputTokens(AiIntentRequest request) {
        return estimate(request.request())
                + estimate(request.lastSelectionTarget())
                + estimate(request.currentTime())
                + estimate(request.timeZone())
                + estimate(request.locale());
    }

    public int estimateInputTokens(AiChatRequest request) {
        long total = (long) estimate(request.question())
                + estimate(request.pageTitle())
                + estimate(request.pageDescription())
                + estimate(request.pageText());
        if (request.history() != null) {
            for (AiChatMessage message : request.history()) {
                if (message != null) total += estimate(message.text());
            }
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    @Transactional(readOnly = true)
    public FamilyAiUsageResponse current(UUID userId) {
        Instant now = Instant.now();
        boolean special = activeSpecialGrant(userId, now).isPresent();
        int activeUsers = Math.toIntExact(grantRepository.countActiveAt(now));
        String period = periodKey(now);
        long poolBudget = monthlyBudgetMicrorupees();
        long allowance = activeUsers == 0 ? 0 : poolBudget / activeUsers;
        long poolSpent = poolRepository.findById(period)
                .map(FamilyAiPoolUsageEntity::getSpentMicrorupees)
                .orElse(0L);
        long globalRemaining = Math.max(0L, poolBudget - Math.min(poolBudget, poolSpent));
        long spent = special
                ? userUsageRepository.findByUserIdAndPeriodKey(userId, period)
                        .map(FamilyAiUserUsageEntity::getSpentMicrorupees)
                        .orElse(0L)
                : 0L;
        long individualRemaining = Math.max(0L, allowance - spent);
        long remaining = special ? Math.min(individualRemaining, globalRemaining) : 0L;
        double percent = allowance <= 0 ? 0.0 : Math.min(100.0, (spent * 100.0) / allowance);
        return new FamilyAiUsageResponse(
                special,
                activeUsers,
                poolBudget,
                allowance,
                spent,
                remaining,
                percent,
                period,
                resetsAt(now),
                special ? (remaining > 0 ? "ACTIVE" : "LIMIT_REACHED") : "NOT_SPECIAL"
        );
    }

    @Transactional
    public boolean consumeRequestBudget(
            UUID userId,
            int estimatedInputTokens,
            int maxProviderCalls,
            int maxOutputTokensPerCall
    ) {
        Instant now = Instant.now();
        Optional<SpecialPremiumGrantEntity> specialGrant = activeSpecialGrant(userId, now);
        if (specialGrant.isEmpty()) {
            return false;
        }

        // PREMIUM_SPECIAL is a single, permanent catalogue row. Locking it gives
        // every Friends & Family request the same database-portable serialization
        // point before creating/updating monthly usage rows. The transaction ends
        // before the provider call, so the lock is never held during network I/O.
        planRepository.findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL)
                .orElseThrow(() -> new IllegalStateException("Premium Special plan is unavailable"));

        String period = periodKey(now);
        FamilyAiPoolUsageEntity pool = poolRepository.findById(period)
                .orElseGet(() -> {
                    FamilyAiPoolUsageEntity created = new FamilyAiPoolUsageEntity();
                    created.setPeriodKey(period);
                    created.setSpentMicrorupees(0L);
                    return poolRepository.saveAndFlush(created);
                });

        FamilyAiUserUsageEntity usage = userUsageRepository.findByUserIdAndPeriodKey(userId, period)
                .orElseGet(() -> {
                    FamilyAiUserUsageEntity created = new FamilyAiUserUsageEntity();
                    created.setUser(specialGrant.orElseThrow().getUser());
                    created.setPeriodKey(period);
                    created.setSpentMicrorupees(0L);
                    return userUsageRepository.saveAndFlush(created);
                });

        int activeUsers = Math.toIntExact(grantRepository.countActiveAt(now));
        if (activeUsers <= 0) {
            throw familyLimitReached();
        }
        long totalBudget = monthlyBudgetMicrorupees();
        long userAllowance = totalBudget / activeUsers;
        long debit = requestBudgetMicrorupees(estimatedInputTokens, maxProviderCalls, maxOutputTokensPerCall);

        if (safeAdd(pool.getSpentMicrorupees(), debit) > totalBudget
                || safeAdd(usage.getSpentMicrorupees(), debit) > userAllowance) {
            throw familyLimitReached();
        }

        pool.setSpentMicrorupees(safeAdd(pool.getSpentMicrorupees(), debit));
        usage.setSpentMicrorupees(safeAdd(usage.getSpentMicrorupees(), debit));
        poolRepository.save(pool);
        userUsageRepository.save(usage);
        return true;
    }

    private int estimate(String value) {
        if (value == null || value.isBlank()) return 0;
        // UTF-8 byte length is deliberately conservative versus typical GPT token counts.
        return Math.max(1, value.getBytes(StandardCharsets.UTF_8).length);
    }

    private Optional<SpecialPremiumGrantEntity> activeSpecialGrant(UUID userId, Instant now) {
        return grantRepository.findByUserId(userId)
                .filter(SpecialPremiumGrantEntity::isActive)
                .filter(grant -> grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
    }

    private long requestBudgetMicrorupees(int inputTokens, int maxProviderCalls, int maxOutputTokensPerCall) {
        long providerInputTokens = Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, (long) inputTokens + PROVIDER_OVERHEAD_TOKENS)
        );
        BigDecimal oneCallUsd = tokenCostUsd(providerInputTokens, maxOutputTokensPerCall);
        return oneCallUsd
                .multiply(BigDecimal.valueOf(Math.max(1, maxProviderCalls)))
                .multiply(properties.usdInrAccountingRate())
                .multiply(SAFETY_MULTIPLIER)
                .multiply(BigDecimal.valueOf(MICRORUPEES_PER_RUPEE))
                .setScale(0, RoundingMode.CEILING)
                .max(BigDecimal.ONE)
                .longValueExact();
    }

    private BigDecimal tokenCostUsd(long inputTokens, long outputTokens) {
        BigDecimal inputUsd = BigDecimal.valueOf(inputTokens)
                .multiply(properties.inputUsdPerMillionTokens())
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal outputUsd = BigDecimal.valueOf(Math.max(0L, outputTokens))
                .multiply(properties.outputUsdPerMillionTokens())
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        return inputUsd.add(outputUsd);
    }

    private long monthlyBudgetMicrorupees() {
        return Math.multiplyExact(properties.monthlyBudgetRupees(), MICRORUPEES_PER_RUPEE);
    }

    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(Math.max(0L, left), Math.max(0L, right));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private String periodKey(Instant now) {
        return YearMonth.from(now.atZone(ZoneOffset.UTC)).toString();
    }

    private Instant resetsAt(Instant now) {
        return YearMonth.from(now.atZone(ZoneOffset.UTC))
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    private ApiException familyLimitReached() {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "FAMILY_AI_BUDGET_REACHED",
                "Your Friends & Family AI allowance has been used for this month."
        );
    }
}
