package com.email.server.delivery;

public class DeliveryStats {
    private final long totalQueued;
    private final long totalDelivered;
    private final long totalFailed;
    private final long totalRetries;
    private final boolean running;

    public DeliveryStats(long totalQueued, long totalDelivered, long totalFailed, long totalRetries, boolean running) {
        this.totalQueued = totalQueued;
        this.totalDelivered = totalDelivered;
        this.totalFailed = totalFailed;
        this.totalRetries = totalRetries;
        this.running = running;
    }

    public long getTotalQueued() {
        return totalQueued;
    }

    public long getTotalDelivered() {
        return totalDelivered;
    }

    public long getTotalFailed() {
        return totalFailed;
    }

    public long getTotalRetries() {
        return totalRetries;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public String toString() {
        return "DeliveryStats{" +
                "totalQueued=" + totalQueued +
                ", totalDelivered=" + totalDelivered +
                ", totalFailed=" + totalFailed +
                ", totalRetries=" + totalRetries +
                ", running=" + running +
                '}';
    }
}