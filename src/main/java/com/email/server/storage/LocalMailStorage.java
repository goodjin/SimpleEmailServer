package com.email.server.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LocalMailStorage implements MailStorage {
    private static final Logger logger = LoggerFactory.getLogger(LocalMailStorage.class);
    private static final String MESSAGE_ID_PREFIX = "MSG";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String basePath;
    private final Map<String, MailMessage> messageCache = new ConcurrentHashMap<>();
    private final AtomicLong messageCounter = new AtomicLong(0);
    private final AtomicLong totalSize = new AtomicLong(0);

    public LocalMailStorage(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void initialize() throws MailStorageException {
        try {
            Path path = Paths.get(basePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created mail storage directory: {}", basePath);
            }

            // Load existing messages
            loadExistingMessages();
            logger.info("Local mail storage initialized with {} messages", messageCache.size());
        } catch (IOException e) {
            throw new MailStorageException("Failed to initialize mail storage", e);
        }
    }

    @Override
    public String saveMail(String from, List<String> recipients, String mailData) throws MailStorageException {
        String messageId = generateMessageId();

        try {
            MailMessage message = new MailMessage(messageId, from, recipients, mailData);

            // Save to file
            saveToFile(message);

            // Cache in memory
            messageCache.put(messageId, message);
            totalSize.addAndGet(message.getSize());

            logger.info("Saved mail {} (size: {} bytes, from: {}, recipients: {})",
                    messageId, message.getSize(), from, recipients.size());

            return messageId;
        } catch (IOException e) {
            throw new MailStorageException("Failed to save mail: " + messageId, e);
        }
    }

    @Override
    public MailMessage getMail(String messageId) throws MailStorageException {
        MailMessage message = messageCache.get(messageId);
        if (message != null) {
            return message;
        }

        try {
            Path storagePath = getMessageDirectoryPath(messageId);
            Path emlPath = storagePath.resolve(messageId + ".eml");
            if (Files.exists(emlPath)) {
                message = loadFromFile(emlPath);
                if (message != null) {
                    messageCache.put(messageId, message);
                    return message;
                }
            }
            return null;
        } catch (Exception e) {
            throw new MailStorageException("Failed to load mail: " + messageId, e);
        }
    }

    @Override
    public boolean deleteMail(String messageId) throws MailStorageException {
        MailMessage message = messageCache.remove(messageId);
        boolean deletedFromCache = (message != null);

        if (deletedFromCache) {
            totalSize.addAndGet(-message.getSize());
        }

        Path storagePath = getMessageDirectoryPath(messageId);
        boolean filesDeleted = false;
        try {
            // Delete .eml and .meta files
            filesDeleted = Files.deleteIfExists(storagePath.resolve(messageId + ".eml"));
            filesDeleted = Files.deleteIfExists(storagePath.resolve(messageId + ".meta")) || filesDeleted;

            // Try to delete directory if empty
            Files.deleteIfExists(storagePath);
            logger.info("Deleted mail files and attempted directory deletion for: {}", messageId);
        } catch (IOException e) {
            logger.error("Failed to delete message files for: " + messageId, e);
            throw new MailStorageException("Failed to delete mail files: " + messageId, e);
        }

        if (deletedFromCache || filesDeleted) {
            logger.info("Mail {} deletion process completed.", messageId);
            return true;
        }

        logger.warn("Mail {} not found in cache or on disk for deletion.", messageId);
        return false;
    }

    @Override
    public boolean isAvailable() {
        try {
            Path path = Paths.get(basePath);
            return Files.exists(path) && Files.isWritable(path);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public StorageStats getStats() {
        long availableSpace = 0;
        boolean healthy = false;

        try {
            Path path = Paths.get(basePath);
            if (Files.exists(path)) {
                availableSpace = Files.getFileStore(path).getUsableSpace();
                healthy = true;
            }
        } catch (IOException e) {
            logger.error("Error getting storage stats", e);
        }

        return new StorageStats(
                messageCache.size(),
                totalSize.get(),
                availableSpace,
                healthy);
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down local mail storage ({} messages)", messageCache.size());
        messageCache.clear();
    }

    private String generateMessageId() {
        String date = LocalDate.now().format(DATE_FORMATTER);
        long counter = messageCounter.incrementAndGet();
        return MESSAGE_ID_PREFIX + date + String.format("%08d", counter);
    }

    private void saveToFile(MailMessage message) throws IOException {
        Path storagePath = getMessageDirectoryPath(message.getMessageId());
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
        }
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
        }

        // Save content to .eml file
        Path emlPath = storagePath.resolve(message.getMessageId() + ".eml");
        Files.write(emlPath, message.getData().getBytes(StandardCharsets.UTF_8));

        // Save metadata to .meta file
        Path metaPath = storagePath.resolve(message.getMessageId() + ".meta");
        Properties props = new Properties();
        props.setProperty("from", message.getFrom());
        props.setProperty("recipients", String.join(",", message.getRecipients()));
        props.setProperty("receivedTime", message.getReceivedTime().toString());
        props.setProperty("flags", String.join(",", message.getFlags()));

        try (java.io.Writer writer = Files.newBufferedWriter(metaPath)) {
            props.store(writer, "Message Metadata");
        }
    }

    private MailMessage loadFromFile(Path path) {
        try {
            String filename = path.getFileName().toString();
            if (!filename.endsWith(".eml")) {
                return null;
            }

            String messageId = filename.substring(0, filename.length() - 4);
            Path storagePath = getMessageDirectoryPath(messageId);
            Path metaPath = storagePath.resolve(messageId + ".meta");

            if (!Files.exists(metaPath)) {
                logger.warn("Metadata file missing for message: {}", messageId);
                return null;
            }

            Properties props = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(metaPath)) {
                props.load(reader);
            }

            String from = props.getProperty("from", "");
            String recipientsStr = props.getProperty("recipients", "");
            List<String> recipients = Arrays.asList(recipientsStr.split(","));
            String timeStr = props.getProperty("receivedTime");
            LocalDateTime receivedTime = timeStr != null ? LocalDateTime.parse(timeStr) : LocalDateTime.now();

            String data = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

            MailMessage message = new MailMessage(messageId, from, recipients, data, receivedTime);

            String flagsStr = props.getProperty("flags", "");
            if (!flagsStr.isEmpty()) {
                Set<String> flags = new HashSet<>(Arrays.asList(flagsStr.split(",")));
                message.setFlags(flags);
            }

            return message;
        } catch (Exception e) {
            logger.error("Failed to load message from " + path, e);
            return null;
        }
    }

    private Path getMessageDirectoryPath(String messageId) {
        // Organize files by date for better performance
        String date = messageId.substring(MESSAGE_ID_PREFIX.length(), MESSAGE_ID_PREFIX.length() + 8);
        String year = date.substring(0, 4);
        String month = date.substring(4, 6);

        // This method should return the directory where the message files (.eml, .meta)
        // are stored.
        // The original implementation returned a path ending with ".msg", which implies
        // a file,
        // but it was used as a directory. Let's correct it to be a directory path.
        return Paths.get(basePath, year, month, messageId);
    }

    private void loadExistingMessages() throws IOException {
        Path basePathObj = Paths.get(basePath);
        if (!Files.exists(basePathObj)) {
            return;
        }

        Files.walk(basePathObj)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".eml")) // Filter for .eml files
                .forEach(path -> {
                    try {
                        MailMessage message = loadFromFile(path);
                        if (message != null) {
                            messageCache.put(message.getMessageId(), message);
                            totalSize.addAndGet(message.getSize());

                            // Update counter based on existing messages
                            String messageId = message.getMessageId();
                            String counterStr = messageId.substring(MESSAGE_ID_PREFIX.length() + 8);
                            try {
                                long counter = Long.parseLong(counterStr);
                                messageCounter.updateAndGet(current -> Math.max(current, counter));
                            } catch (NumberFormatException e) {
                                // Ignore invalid message IDs
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error loading message from file: " + path, e);
                    }
                });
    }

    @Override
    public List<MailMessage> getMessagesForUser(String username) throws MailStorageException {
        // Messages are already loaded in cache
        List<MailMessage> userMessages = new ArrayList<>();
        for (MailMessage message : messageCache.values()) {
            if (message.getRecipients().contains(username)) {
                userMessages.add(message);
            }
        }
        userMessages.sort(Comparator.comparing(MailMessage::getReceivedTime));
        return userMessages;
    }

    @Override
    public void updateFlags(String messageId, Set<String> flags, boolean replace) throws MailStorageException {
        MailMessage message = messageCache.get(messageId);
        if (message == null) {
            throw new MailStorageException("Message not found: " + messageId);
        }

        synchronized (message) {
            if (replace) {
                message.setFlags(flags);
            } else {
                Set<String> currentFlags = message.getFlags();
                currentFlags.addAll(flags);
                message.setFlags(currentFlags);
            }
        }

        try {
            saveToFile(message);
        } catch (IOException e) {
            throw new MailStorageException("Failed to save message flags", e);
        }
    }
}