package com.waypoint.backend.model.admin;

import com.waypoint.backend.model.subscription.SubscriptionStatus;

import java.time.Instant;

public final class AdminSubscriptionUpdateRequest {
    private SubscriptionStatus status;
    private Instant renewsAt;
    private Instant endsAt;
    private boolean clearRenewsAt;
    private boolean clearEndsAt;

    public AdminSubscriptionUpdateRequest() {
    }

    public SubscriptionStatus status() { return status; }
    public Instant renewsAt() { return renewsAt; }
    public Instant endsAt() { return endsAt; }
    public boolean clearRenewsAt() { return clearRenewsAt; }
    public boolean clearEndsAt() { return clearEndsAt; }

    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public void setRenewsAt(Instant renewsAt) { this.renewsAt = renewsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setClearRenewsAt(boolean clearRenewsAt) { this.clearRenewsAt = clearRenewsAt; }
    public void setClearEndsAt(boolean clearEndsAt) { this.clearEndsAt = clearEndsAt; }
}
