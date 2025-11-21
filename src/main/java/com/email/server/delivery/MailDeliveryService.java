package com.email.server.delivery;

import java.util.List;

public interface MailDeliveryService {
    /**
     * Queue a message for delivery
     * @param messageId Message ID to deliver
     * @param recipients List of recipient addresses
     */
    void queueDelivery(String messageId, List<String> recipients);

    /**
     * Start the delivery service
     */
    void start();

    /**
     * Stop the delivery service
     */
    void stop();

    /**
     * Get delivery statistics
     * @return Delivery statistics
     */
    DeliveryStats getStats();

    /**
     * Check if service is running
     * @return true if running
     */
    boolean isRunning();
}