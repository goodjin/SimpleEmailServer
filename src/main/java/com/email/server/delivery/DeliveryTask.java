package com.email.server.delivery;

import java.time.LocalDateTime;
import java.util.List;

public class DeliveryTask {
    public enum Status {
        QUEUED,
        IN_PROGRESS,
        DELIVERED,
        FAILED,
        RETRY
    }

    private final String messageId;
    private final List<String> recipients;
    private final LocalDateTime createdTime;
    private volatile Status status;
    private volatile int retryCount;
    private volatile LocalDateTime lastAttemptTime;
    private volatile String lastError;

    public DeliveryTask(String messageId, List<String> recipients) {
        this.messageId = messageId;
        this.recipients = recipients;
        this.createdTime = LocalDateTime.now();
        this.status = Status.QUEUED;
        this.retryCount = 0;
    }

    public String getMessageId() {
        return messageId;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public LocalDateTime getLastAttemptTime() {
        return lastAttemptTime;
    }

    public void setLastAttemptTime(LocalDateTime lastAttemptTime) {
        this.lastAttemptTime = lastAttemptTime;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public boolean shouldRetry() {
        return retryCount < 3 && status == Status.FAILED;
    }

    @Override
    public String toString() {
        return "DeliveryTask{" +
                "messageId='" + messageId + '\'' +
                ", recipients=" + recipients +
                ", status=" + status +
                ", retryCount=" + retryCount +
                '}';
    }
}