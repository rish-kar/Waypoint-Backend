package com.waypoint.backend.model.ai;

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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "family_ai_user_usage",
        uniqueConstraints = @UniqueConstraint(name = "uq_family_ai_user_period", columnNames = {"user_id", "period_key"})
)
public class FamilyAiUserUsageEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "period_key", nullable = false, length = 7)
    private String periodKey;

    @Column(nullable = false)
    private long spentMicrorupees;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }
    public long getSpentMicrorupees() { return spentMicrorupees; }
    public void setSpentMicrorupees(long spentMicrorupees) { this.spentMicrorupees = spentMicrorupees; }
    public Instant getUpdatedAt() { return updatedAt; }
}
