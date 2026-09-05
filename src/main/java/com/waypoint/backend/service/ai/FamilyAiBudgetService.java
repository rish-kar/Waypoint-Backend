package com.waypoint.backend.service.ai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.waypoint.backend.config.ai.FamilyAiAccessProperties;
import com.waypoint.backend.model.admin.AdminFamilyAiUsageResponse;
import com.waypoint.backend.model.admin.AdminFamilyAiUserUsageResponse;
import com.waypoint.backend.model.ai.AiChatMessage;
import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.FamilyAiUsageResponse;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.utilities.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

@Service
public class FamilyAiBudgetService {
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal SAFETY_MULTIPLIER = new BigDecimal("2.0");
    private static final long MICRORUPEES_PER_RUPEE = 1_000_000L;
    private static final int MAX_SPECIAL_REQUEST_INPUT_TOKENS = 5_000;
    private static final int PROVIDER_OVERHEAD_TOKENS = 5_000;
    private static final Duration SESSION_WINDOW = Duration.ofHours(5);
    private static final Duration WEEKLY_WINDOW = Duration.ofDays(7);
    private static final Encoding GPT5_ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.O200K_BASE);

    private final FamilyAiAccessProperties properties;
    private final SpecialPremiumGrantRepository grantRepository;
    private final PlanRepository planRepository;

    public FamilyAiBudgetService(
            FamilyAiAccessProperties properties,
            SpecialPremiumGrantRepository grantRepository,
            PlanRepository planRepository
    ) {
        this.properties = properties;
        this.grantRepository = grantRepository;
        this.planRepository = planRepository;
    }

    public int estimateInputTokens(AiIntentRequest request) {
        long total = (long) countTokens(request.request())
                + countTokens(request.lastSelectionTarget())
                + countTokens(request.currentTime())
                + countTokens(request.timeZone())
                + countTokens(request.locale());
        return clampTokenCount(total);
    }

    public int estimateInputTokens(AiChatRequest request) {
        long total = (long) countTokens(request.question())
                + countTokens(request.pageTitle())
                + countTokens(request.pageDescription())
                + countTokens(request.pageText());
        if (request.history() != null) {
            for (AiChatMessage message : request.history()) {
                if (message != null) total += countTokens(message.text());
                if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
            }
        }
        return clampTokenCount(total);
    }

    @Transactional(readOnly = true)
    public FamilyAiUsageResponse current(UUID userId) {
        Instant now = Instant.now();
        Optional<SpecialPremiumGrantEntity> specialGrant = activeSpecialGrant(userId, now);
        if (specialGrant.isEmpty()) {
            return new FamilyAiUsageResponse(false, 0, 5, 0.0, null, 7, 0.0, null, "NOT_SPECIAL");
        }

        String period = periodKey(now);
        int activeUsers = Math.toIntExact(grantRepository.countActiveAt(now));
        long poolBudget = monthlyBudgetMicrorupees();
        long allowance = activeUsers <= 0 ? 0L : poolBudget / activeUsers;
        long poolSpent = normalizeSpent(grantRepository.sumAiSpentMicrorupeesForPeriod(period));
        long poolRemaining = remaining(poolBudget, poolSpent);
        SpecialPremiumGrantEntity grant = specialGrant.get();
        long monthlySpent = currentPeriodSpend(grant, period);
        long monthlyRemaining = Math.min(remaining(allowance, monthlySpent), poolRemaining);

        WindowUsage session = sessionUsage(grant, now);
        WindowUsage weekly = weeklyUsage(grant, now);
        long sessionLimit = budgetPercent(allowance, properties.sessionBudgetPercent());
        long weeklyLimit = budgetPercent(allowance, properties.weeklyBudgetPercent());
        long sessionRemaining = Math.min(remaining(sessionLimit, session.spentMicrorupees()), monthlyRemaining);
        long weeklyRemaining = Math.min(remaining(weeklyLimit, weekly.spentMicrorupees()), monthlyRemaining);

        String status;
        if (monthlyRemaining <= 0L) status = "ACCOUNT_LIMIT_REACHED";
        else if (sessionRemaining <= 0L) status = "SESSION_LIMIT_REACHED";
        else if (weeklyRemaining <= 0L) status = "WEEKLY_LIMIT_REACHED";
        else status = "ACTIVE";

        return new FamilyAiUsageResponse(
                true,
                MAX_SPECIAL_REQUEST_INPUT_TOKENS,
                Math.toIntExact(SESSION_WINDOW.toHours()),
                percentage(session.spentMicrorupees(), sessionLimit),
                session.resetsAt(),
                Math.toIntExact(WEEKLY_WINDOW.toDays()),
                percentage(weekly.spentMicrorupees(), weeklyLimit),
                weekly.resetsAt(),
                status
        );
    }

    @Transactional(readOnly = true)
    public AdminFamilyAiUsageResponse adminCurrent() {
        Instant now = Instant.now();
        String period = periodKey(now);
        int activeUsers = Math.toIntExact(grantRepository.countActiveAt(now));
        long poolBudget = monthlyBudgetMicrorupees();
        long poolSpent = normalizeSpent(grantRepository.sumAiSpentMicrorupeesForPeriod(period));
        long poolRemaining = remaining(poolBudget, poolSpent);
        long activeAllowance = activeUsers <= 0 ? 0L : poolBudget / activeUsers;

        List<AdminFamilyAiUserUsageResponse> users = grantRepository.findAllWithUserForFamilyAiAdmin().stream()
                .map(grant -> adminUserUsage(grant, now, period, activeAllowance, poolRemaining))
                .toList();

        return new AdminFamilyAiUsageResponse(
                poolBudget,
                poolSpent,
                poolRemaining,
                percentage(poolSpent, poolBudget),
                activeUsers,
                MAX_SPECIAL_REQUEST_INPUT_TOKENS,
                Math.toIntExact(SESSION_WINDOW.toHours()),
                properties.sessionBudgetPercent(),
                Math.toIntExact(WEEKLY_WINDOW.toDays()),
                properties.weeklyBudgetPercent(),
                period,
                monthlyResetsAt(now),
                users
        );
    }

    @Transactional
    public boolean consumeRequestBudget(UUID userId, AiIntentRequest request, int maxProviderCalls, int maxOutputTokensPerCall) {
        return consumeRequestBudgetIfSpecial(userId, () -> estimateInputTokens(request), maxProviderCalls, maxOutputTokensPerCall);
    }

    @Transactional
    public boolean consumeRequestBudget(UUID userId, AiChatRequest request, int maxProviderCalls, int maxOutputTokensPerCall) {
        return consumeRequestBudgetIfSpecial(userId, () -> estimateInputTokens(request), maxProviderCalls, maxOutputTokensPerCall);
    }

    @Transactional
    public boolean consumeRequestBudget(UUID userId, int estimatedInputTokens, int maxProviderCalls, int maxOutputTokensPerCall) {
        return consumeRequestBudgetIfSpecial(userId, () -> estimatedInputTokens, maxProviderCalls, maxOutputTokensPerCall);
    }

    private boolean consumeRequestBudgetIfSpecial(
            UUID userId,
            IntSupplier inputTokenCounter,
            int maxProviderCalls,
            int maxOutputTokensPerCall
    ) {
        Instant now = Instant.now();
        if (activeSpecialGrant(userId, now).isEmpty()) return false;

        int estimatedInputTokens = Math.max(0, inputTokenCounter.getAsInt());
        if (estimatedInputTokens > MAX_SPECIAL_REQUEST_INPUT_TOKENS) throw familyRequestTooLarge();

        planRepository.findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL)
                .orElseThrow(() -> new IllegalStateException("Premium Special plan is unavailable"));

        SpecialPremiumGrantEntity grant = grantRepository.findByUserIdForUpdate(userId)
                .filter(value -> isActiveSpecialGrant(value, now))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "AI_ACCESS_DENIED",
                        "Premium Special access is not active for this account."
                ));

        String period = periodKey(now);
        int activeUsers = Math.toIntExact(grantRepository.countActiveAt(now));
        if (activeUsers <= 0) throw familyAccessLimitReached();

        long totalBudget = monthlyBudgetMicrorupees();
        long userAllowance = totalBudget / activeUsers;
        long globalSpent = normalizeSpent(grantRepository.sumAiSpentMicrorupeesForPeriod(period));
        long userSpent = currentPeriodSpend(grant, period);
        long debit = requestBudgetMicrorupees(estimatedInputTokens, maxProviderCalls, maxOutputTokensPerCall);

        WindowUsage session = sessionUsage(grant, now);
        WindowUsage weekly = weeklyUsage(grant, now);
        long sessionLimit = budgetPercent(userAllowance, properties.sessionBudgetPercent());
        long weeklyLimit = budgetPercent(userAllowance, properties.weeklyBudgetPercent());

        if (safeAdd(globalSpent, debit) > totalBudget || safeAdd(userSpent, debit) > userAllowance) {
            throw familyAccessLimitReached();
        }
        if (safeAdd(session.spentMicrorupees(), debit) > sessionLimit) throw familySessionLimitReached();
        if (safeAdd(weekly.spentMicrorupees(), debit) > weeklyLimit) throw familyWeeklyLimitReached();

        boolean sameMonthlyPeriod = period.equals(grant.getAiPeriodKey());
        grant.setAiPeriodKey(period);
        grant.setAiSpentMicrorupees(safeAdd(userSpent, debit));
        grant.setAiPeriodRequestCount(safeAdd(sameMonthlyPeriod ? grant.getAiPeriodRequestCount() : 0L, 1L));
        grant.setAiPeriodInputTokens(safeAdd(sameMonthlyPeriod ? grant.getAiPeriodInputTokens() : 0L, estimatedInputTokens));

        grant.setAiSessionStartedAt(session.startedAt());
        grant.setAiSessionSpentMicrorupees(safeAdd(session.spentMicrorupees(), debit));
        grant.setAiSessionRequestCount(safeAdd(session.requestCount(), 1L));
        grant.setAiSessionInputTokens(safeAdd(session.inputTokens(), estimatedInputTokens));

        grant.setAiWeeklyStartedAt(weekly.startedAt());
        grant.setAiWeeklySpentMicrorupees(safeAdd(weekly.spentMicrorupees(), debit));
        grant.setAiWeeklyRequestCount(safeAdd(weekly.requestCount(), 1L));
        grant.setAiWeeklyInputTokens(safeAdd(weekly.inputTokens(), estimatedInputTokens));

        grantRepository.save(grant);
        return true;
    }

    private AdminFamilyAiUserUsageResponse adminUserUsage(
            SpecialPremiumGrantEntity grant,
            Instant now,
            String period,
            long activeAllowance,
            long poolRemaining
    ) {
        UserEntity user = grant.getUser();
        boolean active = isActiveSpecialGrant(grant, now);
        long spent = currentPeriodSpend(grant, period);
        long allowance = active ? activeAllowance : 0L;
        long userRemaining = active ? Math.min(remaining(allowance, spent), poolRemaining) : 0L;
        boolean sameMonthlyPeriod = period.equals(grant.getAiPeriodKey());
        long monthlyRequests = sameMonthlyPeriod ? Math.max(0L, grant.getAiPeriodRequestCount()) : 0L;
        long monthlyInputTokens = sameMonthlyPeriod ? Math.max(0L, grant.getAiPeriodInputTokens()) : 0L;

        WindowUsage session = sessionUsage(grant, now);
        WindowUsage weekly = weeklyUsage(grant, now);
        long sessionLimit = active ? budgetPercent(allowance, properties.sessionBudgetPercent()) : 0L;
        long weeklyLimit = active ? budgetPercent(allowance, properties.weeklyBudgetPercent()) : 0L;
        long sessionRemaining = active ? Math.min(remaining(sessionLimit, session.spentMicrorupees()), userRemaining) : 0L;
        long weeklyRemaining = active ? Math.min(remaining(weeklyLimit, weekly.spentMicrorupees()), userRemaining) : 0L;

        String status;
        if (!grant.isActive()) status = "REVOKED";
        else if (grant.getValidUntil() != null && !grant.getValidUntil().isAfter(now)) status = "EXPIRED";
        else if (userRemaining <= 0L) status = "ACCOUNT_LIMIT_REACHED";
        else if (sessionRemaining <= 0L) status = "SESSION_LIMIT_REACHED";
        else if (weeklyRemaining <= 0L) status = "WEEKLY_LIMIT_REACHED";
        else status = "ACTIVE";

        return new AdminFamilyAiUserUsageResponse(
                grant.getId(),
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPictureUrl(),
                user.getPhoneNumber(),
                user.getPhoneCountryCode(),
                user.getProvider(),
                user.getProviderUserId(),
                user.getPlan() == null ? null : user.getPlan().getCode(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                active,
                grant.getValidUntil(),
                grant.getReason(),
                grant.getGrantedBy(),
                grant.getGrantedAt(),
                grant.getRevokedBy(),
                grant.getRevokedAt(),
                allowance,
                spent,
                userRemaining,
                active ? percentage(spent, allowance) : 0.0,
                monthlyRequests,
                monthlyInputTokens,
                sessionLimit,
                session.spentMicrorupees(),
                sessionRemaining,
                active ? percentage(session.spentMicrorupees(), sessionLimit) : 0.0,
                session.resetsAt(),
                session.requestCount(),
                session.inputTokens(),
                weeklyLimit,
                weekly.spentMicrorupees(),
                weeklyRemaining,
                active ? percentage(weekly.spentMicrorupees(), weeklyLimit) : 0.0,
                weekly.resetsAt(),
                weekly.requestCount(),
                weekly.inputTokens(),
                status
        );
    }

    private WindowUsage sessionUsage(SpecialPremiumGrantEntity grant, Instant now) {
        return windowUsage(
                grant.getAiSessionStartedAt(),
                grant.getAiSessionSpentMicrorupees(),
                grant.getAiSessionRequestCount(),
                grant.getAiSessionInputTokens(),
                SESSION_WINDOW,
                now
        );
    }

    private WindowUsage weeklyUsage(SpecialPremiumGrantEntity grant, Instant now) {
        return windowUsage(
                grant.getAiWeeklyStartedAt(),
                grant.getAiWeeklySpentMicrorupees(),
                grant.getAiWeeklyRequestCount(),
                grant.getAiWeeklyInputTokens(),
                WEEKLY_WINDOW,
                now
        );
    }

    private WindowUsage windowUsage(
            Instant startedAt,
            long spentMicrorupees,
            long requestCount,
            long inputTokens,
            Duration duration,
            Instant now
    ) {
        boolean current = startedAt != null
                && !startedAt.isAfter(now)
                && now.isBefore(startedAt.plus(duration));
        if (!current) return new WindowUsage(now, now.plus(duration), 0L, 0L, 0L);
        return new WindowUsage(
                startedAt,
                startedAt.plus(duration),
                Math.max(0L, spentMicrorupees),
                Math.max(0L, requestCount),
                Math.max(0L, inputTokens)
        );
    }

    private int countTokens(String value) {
        if (value == null || value.isBlank()) return 0;
        return GPT5_ENCODING.countTokensOrdinary(value);
    }

    private int clampTokenCount(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private Optional<SpecialPremiumGrantEntity> activeSpecialGrant(UUID userId, Instant now) {
        return grantRepository.findByUserId(userId).filter(grant -> isActiveSpecialGrant(grant, now));
    }

    private boolean isActiveSpecialGrant(SpecialPremiumGrantEntity grant, Instant now) {
        return grant.isActive() && (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
    }

    private long currentPeriodSpend(SpecialPremiumGrantEntity grant, String period) {
        if (!period.equals(grant.getAiPeriodKey())) return 0L;
        return Math.max(0L, grant.getAiSpentMicrorupees());
    }

    private long normalizeSpent(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private long remaining(long allowance, long spent) {
        return Math.max(0L, Math.max(0L, allowance) - Math.min(Math.max(0L, allowance), Math.max(0L, spent)));
    }

    private double percentage(long spent, long allowance) {
        if (allowance <= 0L) return spent > 0L ? 100.0 : 0.0;
        return Math.min(100.0, (Math.max(0L, spent) * 100.0) / allowance);
    }

    private long budgetPercent(long allowance, int percent) {
        if (allowance <= 0L || percent <= 0) return 0L;
        return Math.max(1L, BigDecimal.valueOf(allowance)
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.FLOOR)
                .longValueExact());
    }

    private long requestBudgetMicrorupees(int inputTokens, int maxProviderCalls, int maxOutputTokensPerCall) {
        long providerInputTokens = Math.min(Integer.MAX_VALUE, Math.max(0L, (long) inputTokens + PROVIDER_OVERHEAD_TOKENS));
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

    private Instant monthlyResetsAt(Instant now) {
        return YearMonth.from(now.atZone(ZoneOffset.UTC))
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    private ApiException familyRequestTooLarge() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "FAMILY_AI_REQUEST_TOO_LARGE",
                "Premium Special Cloud AI requests are limited to 5,000 input tokens."
        );
    }

    private ApiException familySessionLimitReached() {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "FAMILY_AI_SESSION_LIMIT_REACHED",
                "Your 5-hour Cloud AI limit has been reached. Try again after it resets."
        );
    }

    private ApiException familyWeeklyLimitReached() {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "FAMILY_AI_WEEKLY_LIMIT_REACHED",
                "Your weekly Cloud AI limit has been reached. Try again after it resets."
        );
    }

    private ApiException familyAccessLimitReached() {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "FAMILY_AI_ACCESS_LIMIT_REACHED",
                "Friends & Family Cloud AI usage is currently at its limit."
        );
    }

    private record WindowUsage(
            Instant startedAt,
            Instant resetsAt,
            long spentMicrorupees,
            long requestCount,
            long inputTokens
    ) {}
}
