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

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (grantedAt == null) {
            grantedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAiPeriodKey() {
        return aiPeriodKey;
    }

    public void setAiPeriodKey(String aiPeriodKey) {
        this.aiPeriodKey = aiPeriodKey;
    }

    public long getAiSpentMicrorupees() {
        return aiSpentMicrorupees;
    }

    public void setAiSpentMicrorupees(long aiSpentMicrorupees) {
        this.aiSpentMicrorupees = aiSpentMicrorupees;
    }
}
