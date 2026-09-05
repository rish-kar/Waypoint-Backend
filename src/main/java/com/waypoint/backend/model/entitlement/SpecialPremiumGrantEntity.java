package com.waypoint.backend.model.entitlement;

import com.waypoint.backend.model.user.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "special_premium_grants")
public class SpecialPremiumGrantEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(nullable = false)
    private boolean active;

    private Instant validUntil;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false, length = 100)
    private String grantedBy;

    @Column(nullable = false)
    private Instant grantedAt;

    @Column(length = 100)
    private String revokedBy;

    private Instant revokedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "ai_period_key", length = 7)
    private String aiPeriodKey;

    @Column(name = "ai_spent_microrupees", nullable = false)
    private long aiSpentMicrorupees;

    @Column(name = "ai_period_request_count", nullable = false)
    private long aiPeriodRequestCount;

    @Column(name = "ai_period_input_tokens", nullable = false)
    private long aiPeriodInputTokens;

    @Column(name = "ai_session_started_at")
    private Instant aiSessionStartedAt;

    @Column(name = "ai_session_spent_microrupees", nullable = false)
    private long aiSessionSpentMicrorupees;

    @Column(name = "ai_session_request_count", nullable = false)
    private long aiSessionRequestCount;

    @Column(name = "ai_session_input_tokens", nullable = false)
    private long aiSessionInputTokens;

    @Column(name = "ai_weekly_started_at")
    private Instant aiWeeklyStartedAt;

    @Column(name = "ai_weekly_spent_microrupees", nullable = false)
    private long aiWeeklySpentMicrorupees;

    @Column(name = "ai_weekly_request_count", nullable = false)
    private long aiWeeklyRequestCount;

    @Column(name = "ai_weekly_input_tokens", nullable = false)
    private long aiWeeklyInputTokens;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (grantedAt == null) grantedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getAiPeriodKey() { return aiPeriodKey; }
    public void setAiPeriodKey(String aiPeriodKey) { this.aiPeriodKey = aiPeriodKey; }
    public long getAiSpentMicrorupees() { return aiSpentMicrorupees; }
    public void setAiSpentMicrorupees(long aiSpentMicrorupees) { this.aiSpentMicrorupees = aiSpentMicrorupees; }
    public long getAiPeriodRequestCount() { return aiPeriodRequestCount; }
    public void setAiPeriodRequestCount(long aiPeriodRequestCount) { this.aiPeriodRequestCount = aiPeriodRequestCount; }
    public long getAiPeriodInputTokens() { return aiPeriodInputTokens; }
    public void setAiPeriodInputTokens(long aiPeriodInputTokens) { this.aiPeriodInputTokens = aiPeriodInputTokens; }
    public Instant getAiSessionStartedAt() { return aiSessionStartedAt; }
    public void setAiSessionStartedAt(Instant aiSessionStartedAt) { this.aiSessionStartedAt = aiSessionStartedAt; }
    public long getAiSessionSpentMicrorupees() { return aiSessionSpentMicrorupees; }
    public void setAiSessionSpentMicrorupees(long aiSessionSpentMicrorupees) { this.aiSessionSpentMicrorupees = aiSessionSpentMicrorupees; }
    public long getAiSessionRequestCount() { return aiSessionRequestCount; }
    public void setAiSessionRequestCount(long aiSessionRequestCount) { this.aiSessionRequestCount = aiSessionRequestCount; }
    public long getAiSessionInputTokens() { return aiSessionInputTokens; }
    public void setAiSessionInputTokens(long aiSessionInputTokens) { this.aiSessionInputTokens = aiSessionInputTokens; }
    public Instant getAiWeeklyStartedAt() { return aiWeeklyStartedAt; }
    public void setAiWeeklyStartedAt(Instant aiWeeklyStartedAt) { this.aiWeeklyStartedAt = aiWeeklyStartedAt; }
    public long getAiWeeklySpentMicrorupees() { return aiWeeklySpentMicrorupees; }
    public void setAiWeeklySpentMicrorupees(long aiWeeklySpentMicrorupees) { this.aiWeeklySpentMicrorupees = aiWeeklySpentMicrorupees; }
    public long getAiWeeklyRequestCount() { return aiWeeklyRequestCount; }
    public void setAiWeeklyRequestCount(long aiWeeklyRequestCount) { this.aiWeeklyRequestCount = aiWeeklyRequestCount; }
    public long getAiWeeklyInputTokens() { return aiWeeklyInputTokens; }
    public void setAiWeeklyInputTokens(long aiWeeklyInputTokens) { this.aiWeeklyInputTokens = aiWeeklyInputTokens; }
}
