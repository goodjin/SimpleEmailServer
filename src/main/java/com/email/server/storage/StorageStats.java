package com.email.server.storage;

public class StorageStats {
    private final long totalMessages;
    private final long totalSize;
    private final long availableSpace;
    private final boolean healthy;

    public StorageStats(long totalMessages, long totalSize, long availableSpace, boolean healthy) {
        this.totalMessages = totalMessages;
        this.totalSize = totalSize;
        this.availableSpace = availableSpace;
        this.healthy = healthy;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public long getAvailableSpace() {
        return availableSpace;
    }

    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public String toString() {
        return "StorageStats{" +
                "totalMessages=" + totalMessages +
                ", totalSize=" + totalSize +
                ", availableSpace=" + availableSpace +
                ", healthy=" + healthy +
                '}';
    }
}