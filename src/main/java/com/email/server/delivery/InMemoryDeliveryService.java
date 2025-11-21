package com.email.server.delivery;

import com.email.server.storage.MailStorage;
import com.email.server.storage.MailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryDeliveryService implements MailDeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryDeliveryService.class);

    private final BlockingQueue<DeliveryTask> deliveryQueue = new LinkedBlockingQueue<>();
    private final Map<String, DeliveryTask> activeTasks = new ConcurrentHashMap<>();
    private final AtomicLong totalQueued = new AtomicLong(0);
    private final AtomicLong totalDelivered = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalRetries = new AtomicLong(0);

    private volatile boolean running = false;
    private ScheduledExecutorService scheduler;
    private ExecutorService deliveryExecutor;
    private MailStorage mailStorage;

    private static final int DELIVERY_THREADS = 3;
    private static final int RETRY_DELAY_SECONDS = 300; // 5 minutes

    @Override
    public void queueDelivery(String messageId, List<String> recipients) {
        if (!running) {
            logger.warn("Delivery service not running, cannot queue message: {}", messageId);
            return;
        }

        DeliveryTask task = new DeliveryTask(messageId, recipients);
        deliveryQueue.offer(task);
        activeTasks.put(messageId, task);
        totalQueued.incrementAndGet();

        logger.info("Queued delivery for message: {} (recipients: {})", messageId, recipients.size());
    }

    @Override
    public void start() {
        if (running) {
            logger.warn("Delivery service already running");
            return;
        }

        running = true;
        scheduler = Executors.newScheduledThreadPool(2);
        deliveryExecutor = Executors.newFixedThreadPool(DELIVERY_THREADS);

        // Start delivery workers
        for (int i = 0; i < DELIVERY_THREADS; i++) {
            scheduler.submit(this::deliveryWorker);
        }

        // Start retry scheduler
        scheduler.scheduleWithFixedDelay(this::processRetries, RETRY_DELAY_SECONDS, RETRY_DELAY_SECONDS, TimeUnit.SECONDS);

        logger.info("Mail delivery service started with {} threads", DELIVERY_THREADS);
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (deliveryExecutor != null) {
            deliveryExecutor.shutdown();
            try {
                if (!deliveryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    deliveryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                deliveryExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Mail delivery service stopped");
    }

    @Override
    public DeliveryStats getStats() {
        return new DeliveryStats(
            totalQueued.get(),
            totalDelivered.get(),
            totalFailed.get(),
            totalRetries.get(),
            running
        );
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public void setMailStorage(MailStorage mailStorage) {
        this.mailStorage = mailStorage;
    }

    private void deliveryWorker() {
        while (running) {
            try {
                DeliveryTask task = deliveryQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processDelivery(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in delivery worker", e);
            }
        }
    }

    private void processDelivery(DeliveryTask task) {
        task.setStatus(DeliveryTask.Status.IN_PROGRESS);
        task.setLastAttemptTime(java.time.LocalDateTime.now());

        try {
            logger.info("Processing delivery for message: {} (attempt: {})",
                       task.getMessageId(), task.getRetryCount() + 1);

            // Get mail message from storage
            if (mailStorage == null) {
                throw new Exception("Mail storage not available");
            }

            MailMessage message = mailStorage.getMail(task.getMessageId());
            if (message == null) {
                throw new Exception("Message not found: " + task.getMessageId());
            }

            // Deliver to each recipient
            boolean allDelivered = true;
            for (String recipient : task.getRecipients()) {
                try {
                    deliverToRecipient(message, recipient);
                } catch (Exception e) {
                    logger.error("Failed to deliver message {} to {}", task.getMessageId(), recipient, e);
                    allDelivered = false;
                }
            }

            if (allDelivered) {
                task.setStatus(DeliveryTask.Status.DELIVERED);
                totalDelivered.incrementAndGet();
                activeTasks.remove(task.getMessageId());
                logger.info("Successfully delivered message: {}", task.getMessageId());
            } else {
                handleDeliveryFailure(task, "Partial delivery failure");
            }

        } catch (Exception e) {
            logger.error("Delivery failed for message: " + task.getMessageId(), e);
            handleDeliveryFailure(task, e.getMessage());
        }
    }

    private void deliverToRecipient(MailMessage message, String recipient) throws Exception {
        String domain = extractDomain(recipient);
        List<String> mxRecords = lookupMxRecords(domain);

        if (mxRecords.isEmpty()) {
            throw new Exception("No MX records found for domain: " + domain);
        }

        // Try each MX record in order of priority
        Exception lastException = null;
        for (String mxHost : mxRecords) {
            try {
                logger.debug("Attempting delivery to {} via MX: {}", recipient, mxHost);
                // In a real implementation, this would connect to the SMTP server
                // For now, we'll simulate successful delivery
                simulateDelivery(message, recipient, mxHost);
                return;
            } catch (Exception e) {
                lastException = e;
                logger.warn("Failed to deliver to {} via {}: {}", recipient, mxHost, e.getMessage());
            }
        }

        if (lastException != null) {
            throw lastException;
        }
    }

    private void simulateDelivery(MailMessage message, String recipient, String mxHost) throws Exception {
        // Simulate network delay
        Thread.sleep(100);

        // Simulate occasional failures for testing
        if (Math.random() < 0.1) { // 10% failure rate
            throw new Exception("Simulated delivery failure to " + mxHost);
        }

        logger.debug("Simulated successful delivery to {} via {}", recipient, mxHost);
    }

    private List<String> lookupMxRecords(String domain) {
        List<String> mxRecords = new ArrayList<>();

        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);

            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute mxAttr = attrs.get("MX");

            if (mxAttr != null) {
                for (int i = 0; i < mxAttr.size(); i++) {
                    String mxRecord = (String) mxAttr.get(i);
                    // MX record format: "10 mail.example.com."
                    String[] parts = mxRecord.split("\\s+");
                    if (parts.length >= 2) {
                        String host = parts[1].endsWith(".") ? parts[1].substring(0, parts[1].length() - 1) : parts[1];
                        mxRecords.add(host);
                    }
                }
            }

            // If no MX records, try A record
            if (mxRecords.isEmpty()) {
                attrs = ctx.getAttributes(domain, new String[]{"A"});
                Attribute aAttr = attrs.get("A");
                if (aAttr != null) {
                    mxRecords.add(domain);
                }
            }

            ctx.close();
        } catch (NamingException e) {
            logger.warn("DNS lookup failed for domain: {}", domain, e);
        }

        logger.debug("MX records for {}: {}", domain, mxRecords);
        return mxRecords;
    }

    private String extractDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex == -1 || atIndex == email.length() - 1) {
            return email;
        }
        return email.substring(atIndex + 1).toLowerCase();
    }

    private void handleDeliveryFailure(DeliveryTask task, String error) {
        task.setStatus(DeliveryTask.Status.FAILED);
        task.setLastError(error);

        if (task.shouldRetry()) {
            task.incrementRetryCount();
            totalRetries.incrementAndGet();
            logger.info("Scheduling retry for message: {} (retry: {})",
                       task.getMessageId(), task.getRetryCount());
        } else {
            totalFailed.incrementAndGet();
            activeTasks.remove(task.getMessageId());
            logger.error("Delivery permanently failed for message: {} after {} retries",
                        task.getMessageId(), task.getRetryCount());
        }
    }

    private void processRetries() {
        if (!running) {
            return;
        }

        // Re-queue failed tasks that should be retried
        List<DeliveryTask> tasksToRetry = new ArrayList<>();
        for (DeliveryTask task : activeTasks.values()) {
            if (task.shouldRetry()) {
                tasksToRetry.add(task);
            }
        }

        for (DeliveryTask task : tasksToRetry) {
            task.setStatus(DeliveryTask.Status.QUEUED);
            deliveryQueue.offer(task);
            logger.debug("Re-queued failed message for retry: {}", task.getMessageId());
        }
    }
}