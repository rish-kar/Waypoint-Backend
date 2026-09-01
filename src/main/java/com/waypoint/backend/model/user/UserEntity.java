package com.waypoint.backend.model.user;

import com.waypoint.backend.model.plan.PlanEntity;

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
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_provider_user", columnNames = {"provider", "provider_user_id"}),
        @UniqueConstraint(name = "uk_users_email_provider", columnNames = {"email", "provider"})
})
public class UserEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    private String displayName;

    @Column(length = 2048)
    private String pictureUrl;

    @Column(length = 32)
    private String phoneNumber;

    @Column(length = 2)
    private String phoneCountryCode;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false)
    private String providerUserId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_code")
    private PlanEntity plan;

    @Column(nullable = false)
    private int aiTrialRequestsUsed;

    @Column(name = "openai_api_key_ciphertext", length = 4096)
    private String openAiApiKeyCiphertext;

    @Column(name = "openai_model", length = 200)
    private String openAiModel;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant lastLoginAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lastLoginAt == null) {
            lastLoginAt = now;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public void setPictureUrl(String pictureUrl) {
        this.pictureUrl = pictureUrl;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhoneCountryCode() {
        return phoneCountryCode;
    }

    public void setPhoneCountryCode(String phoneCountryCode) {
        this.phoneCountryCode = phoneCountryCode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }

    public PlanEntity getPlan() {
        return plan;
    }

    public void setPlan(PlanEntity plan) {
        this.plan = plan;
    }

    public int getAiTrialRequestsUsed() {
        return aiTrialRequestsUsed;
    }

    public void setAiTrialRequestsUsed(int aiTrialRequestsUsed) {
        this.aiTrialRequestsUsed = aiTrialRequestsUsed;
    }

    public String getOpenAiApiKeyCiphertext() {
        return openAiApiKeyCiphertext;
    }

    public void setOpenAiApiKeyCiphertext(String openAiApiKeyCiphertext) {
        this.openAiApiKeyCiphertext = openAiApiKeyCiphertext;
    }

    public String getOpenAiModel() {
        return openAiModel;
    }

    public void setOpenAiModel(String openAiModel) {
        this.openAiModel = openAiModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
