package com.waypoint.backend.service.ai;

import com.waypoint.backend.config.ai.FamilyAiAccessProperties;
import com.waypoint.backend.model.ai.AiChatMessage;
import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.FamilyAiPoolUsageEntity;
import com.waypoint.backend.model.ai.FamilyAiUsageResponse;
import com.waypoint.backend.model.ai.FamilyAiUserUsageEntity;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.repository.ai.FamilyAiPoolUsageRepository;
import com.waypoint.backend.repository.ai.FamilyAiUserUsageRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class FamilyAiBudgetService {
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal SAFETY_MULTIPLIER = new BigDecimal("2.0");
    private static final long MICRORUPEES_PER_RUPEE = 1_000_000L;
    private static final int PROVIDER_OVERHEAD_TOKENS = 5_000;

    private final FamilyAiAccessProperties properties;
    private final SpecialPremiumGrantRepository grantRepository;
    private final FamilyAiPoolUsageRepository poolRepository;
    private final FamilyAiUserUsageRepository userUsageRepository;
    private final ThreadLocal<RequestContext> requestContext = new ThreadLocal<>();

    public FamilyAiBudgetService(
            FamilyAiAccessProperties properties,
            SpecialPremiumGrantRepository grantRepository,
            FamilyAiPoolUsageRepository poolRepository,
            FamilyAiUserUsageRepository userUsageRepository
    ) {
        this.properties = properties;
        this.grantRepository = grantRepository;
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
        boolean special = isSpecialAccess(userId, now);
        int activeUsers = Math.toIntExact(grantRepository.countActiveAt(now));
        String period = periodKey(now);
        long poolBudget = monthlyBudgetMicrorupees();
        long allowance = activeUsers == 0 ? 0 : poolBudget / activeUsers;
        long poolCommitted = poolRepository.findById(period)
                .map(pool -> safeAdd(pool.getSpentMicrorupees(), pool.getReservedMicrorupees()))
                .orElse(0L);
        long globalRemaining = Math.max(0L, poolBudget - Math.min(poolBudget, poolCommitted));
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
                0,
                period,
                resetsAt(now),
                special ? (remaining > 0 ? "ACTIVE" : "LIMIT_REACHED") : "NOT_SPECIAL"
        );
    }

    @Transactional
    public boolean beginRequest(
            UUID userId,
            int estimatedInputTokens,
            int maxProviderCalls,
            int maxOutputTokensPerCall
    ) {
        Instant now = Instant.now();
        if (!isSpecialAccess(userId, now)) {
            return false;
        }
        if (requestContext.get() != null) {
            throw new IllegalStateException("Family AI request context already active");
        }

        String period = periodKey(now);
        poolRepository.ensurePeriod(period);
        FamilyAiPoolUsageEntity pool = poolRepository.findForUpdate(period)
                .orElseThrow(() -> new IllegalStateException("Family AI pool could not be initialized"));

        userUsageRepository.ensureUserPeriod(UUID.randomUUID(), userId, period);
        FamilyAiUserUsageEntity usage = userUsageRepository.findForUpdate(userId, period)
                .orElseThrow(() -> new IllegalStateException("Family AI user usage could not be initialized"));

        int activeUsers = Math.toIntExact(grantRepository.countActiveAt(now));
        if (activeUsers <= 0) {
            throw familyLimitReached();
        }
        long totalBudget = monthlyBudgetMicrorupees();
        long userAllowance = totalBudget / activeUsers;
        long reserve = requestBudgetMicrorupees(estimatedInputTokens, maxProviderCalls, maxOutputTokensPerCall);

        if (safeAdd(safeAdd(pool.getSpentMicrorupees(), pool.getReservedMicrorupees()), reserve) > totalBudget
                || safeAdd(safeAdd(usage.getSpentMicrorupees(), usage.getReservedMicrorupees()), reserve) > userAllowance) {
            throw familyLimitReached();
        }

        pool.setReservedMicrorupees(safeAdd(pool.getReservedMicrorupees(), reserve));
        usage.setReservedMicrorupees(safeAdd(usage.getReservedMicrorupees(), reserve));
        poolRepository.save(pool);
        userUsageRepository.save(usage);
        requestContext.set(new RequestContext(userId, period, reserve));
        return true;
    }

    @Transactional
    public void finishRequest() {
        RequestContext context = requestContext.get();
        if (context == null) return;
        try {
            FamilyAiPoolUsageEntity pool = poolRepository.findForUpdate(context.periodKey)
                    .orElseThrow(() -> new IllegalStateException("Family AI pool not found"));
            FamilyAiUserUsageEntity usage = userUsageRepository.findForUpdate(context.userId, context.periodKey)
                    .orElseThrow(() -> new IllegalStateException("Family AI user usage not found"));

            pool.setReservedMicrorupees(Math.max(0L, pool.getReservedMicrorupees() - context.reservedMicrorupees));
            usage.setReservedMicrorupees(Math.max(0L, usage.getReservedMicrorupees() - context.reservedMicrorupees));
            pool.setSpentMicrorupees(safeAdd(pool.getSpentMicrorupees(), context.reservedMicrorupees));
            usage.setSpentMicrorupees(safeAdd(usage.getSpentMicrorupees(), context.reservedMicrorupees));
            poolRepository.save(pool);
            userUsageRepository.save(usage);
        } finally {
            requestContext.remove();
        }
    }

    private int estimate(String value) {
        if (value == null || value.isBlank()) return 0;
        // GPT tokenization is byte based. Counting UTF-8 bytes is a conservative upper bound
        // for user-supplied input and avoids imposing an unrelated per-request token quota.
        return Math.max(1, value.getBytes(StandardCharsets.UTF_8).length);
    }

    private boolean isSpecialAccess(UUID userId, Instant now) {
        return grantRepository.findByUserId(userId)
                .filter(SpecialPremiumGrantEntity::isActive)
                .map(grant -> grant.getValidUntil() == null || grant.getValidUntil().isAfter(now))
                .orElse(false);
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

    private IllegalStateException impossibleBudgetState() {
        return new IllegalStateException("Family AI budget state is invalid");
    }

    private com.waypoint.backend.utilities.exception.ApiException familyLimitReached() {
        return new com.waypoint.backend.utilities.exception.ApiException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "FAMILY_AI_BUDGET_REACHED",
                "Your Friends & Family AI allowance has been used for this month."
        );
    }

    private static final class RequestContext {
        private final UUID userId;
        private final String periodKey;
        private final long reservedMicrorupees;

        private RequestContext(UUID userId, String periodKey, long reservedMicrorupees) {
            this.userId = userId;
            this.periodKey = periodKey;
            this.reservedMicrorupees = reservedMicrorupees;
        }
    }
}
