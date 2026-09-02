package com.waypoint.backend.model.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "family_ai_pool_usage")
public class FamilyAiPoolUsageEntity {
    @Id
    @Column(length = 7)
    private String periodKey;

    @Column(nullable = false)
    private long spentMicrorupees;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }
    public long getSpentMicrorupees() { return spentMicrorupees; }
    public void setSpentMicrorupees(long spentMicrorupees) { this.spentMicrorupees = spentMicrorupees; }
    public Instant getUpdatedAt() { return updatedAt; }
}
