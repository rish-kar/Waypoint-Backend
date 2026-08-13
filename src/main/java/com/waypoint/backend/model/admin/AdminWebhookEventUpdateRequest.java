package com.waypoint.backend.model.admin;

import com.waypoint.backend.model.webhook.ProcessingStatus;

import java.time.Instant;

public final class AdminWebhookEventUpdateRequest {
    private ProcessingStatus processingStatus;
    private String errorMessage;
    private Instant processedAt;
    private boolean clearErrorMessage;
    private boolean clearProcessedAt;

    public AdminWebhookEventUpdateRequest() {
    }

    public ProcessingStatus processingStatus() { return processingStatus; }
    public String errorMessage() { return errorMessage; }
    public Instant processedAt() { return processedAt; }
    public boolean clearErrorMessage() { return clearErrorMessage; }
    public boolean clearProcessedAt() { return clearProcessedAt; }

    public void setProcessingStatus(ProcessingStatus processingStatus) { this.processingStatus = processingStatus; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public void setClearErrorMessage(boolean clearErrorMessage) { this.clearErrorMessage = clearErrorMessage; }
    public void setClearProcessedAt(boolean clearProcessedAt) { this.clearProcessedAt = clearProcessedAt; }
}
