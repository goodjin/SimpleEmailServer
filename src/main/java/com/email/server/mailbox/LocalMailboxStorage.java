package com.email.server.mailbox;

import com.email.server.storage.MailMessage;
import com.email.server.storage.MailStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalMailboxStorage implements MailboxStorage {
    private static final Logger logger = LoggerFactory.getLogger(LocalMailboxStorage.class);
    private static final String MAILBOX_META_FILE = ".meta";
    private static final String FOLDER_INDEX_FILE = "index";
    private static final String MESSAGE_ID_PREFIX = "MSG";
    private static final String CONTENT_EXTENSION = ".eml";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String basePath;
    private final Map<String, Mailbox> mailboxCache = new ConcurrentHashMap<>();

    // Index caching with thread-safe locks
    private final Map<String, List<MessageMetadata>> indexCache = new ConcurrentHashMap<>();
    private final Map<String, ReadWriteLock> indexLocks = new ConcurrentHashMap<>();

    public LocalMailboxStorage(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void initialize() throws MailStorageException {
        try {
            Path path = Paths.get(basePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created mailboxes directory: {}", basePath);
            }

            // Load existing mailboxes
            loadExistingMailboxes();
            logger.info("Mailbox storage initialized with {} mailboxes", mailboxCache.size());
        } catch (IOException e) {
            throw new MailStorageException("Failed to initialize mailbox storage", e);
        }
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down mailbox storage ({} mailboxes)", mailboxCache.size());
        mailboxCache.clear();
    }

    @Override
    public Mailbox getOrCreateMailbox(String email, String recipientEmail) throws MailStorageException {
        Mailbox mailbox = mailboxCache.get(email);
        if (mailbox != null) {
            return mailbox;
        }

        try {
            Path mailboxPath = getMailboxPath(email);
            if (Files.exists(mailboxPath.resolve(MAILBOX_META_FILE))) {
                mailbox = loadMailbox(email);
            } else {
                mailbox = createMailbox(email, email);
            }

            mailboxCache.put(email, mailbox);
            return mailbox;
        } catch (IOException e) {
            throw new MailStorageException("Failed to get or create mailbox for: " + email, e);
        }
    }

    @Override
    public String saveMessage(String email, String folder, MailMessage message) throws MailStorageException {
        try {
            Mailbox mailbox = getOrCreateMailbox(email, email);
            if (!mailbox.hasFolder(folder)) {
                throw new MailStorageException("Folder does not exist: " + folder);
            }

            String messageId = message.getMessageId();
            if (messageId == null || messageId.isEmpty()) {
                messageId = generateMessageId();
            }

            Path folderPath = getFolderPath(email, folder);
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            // Save .eml file
            Path emlPath = folderPath.resolve(messageId + CONTENT_EXTENSION);
            Files.write(emlPath, message.getData().getBytes(StandardCharsets.UTF_8));

            // Update index
            updateFolderIndex(email, folder, message, messageId);

            logger.info("Saved message {} to {}/{}", messageId, email, folder);
            return messageId;
        } catch (IOException e) {
            throw new MailStorageException("Failed to save message", e);
        }
    }

    @Override
    public List<MailMessage> getMessages(String email, String folder) throws MailStorageException {
        try {
            List<MailMessage> messages = new ArrayList<>();
            Path folderPath = getFolderPath(email, folder);

            if (!Files.exists(folderPath)) {
                return messages;
            }

            List<MessageMetadata> metadataList = loadFolderIndex(email, folder);
            for (MessageMetadata metadata : metadataList) {
                Path emlPath = folderPath.resolve(metadata.getMessageId() + CONTENT_EXTENSION);
                if (Files.exists(emlPath)) {
                    MailMessage message = loadMessage(emlPath, metadata);
                    messages.add(message);
                }
            }

            return messages;
        } catch (IOException e) {
            throw new MailStorageException("Failed to get messages", e);
        }
    }

    @Override
    public MailMessage getMessage(String email, String folder, String messageId) throws MailStorageException {
        try {
            Path emlPath = getFolderPath(email, folder).resolve(messageId + CONTENT_EXTENSION);
            if (!Files.exists(emlPath)) {
                return null;
            }

            List<MessageMetadata> metadataList = loadFolderIndex(email, folder);
            MessageMetadata metadata = metadataList.stream()
                    .filter(m -> m.getMessageId().equals(messageId))
                    .findFirst()
                    .orElse(null);

            if (metadata == null) {
                return null;
            }

            return loadMessage(emlPath, metadata);
        } catch (IOException e) {
            throw new MailStorageException("Failed to get message: " + messageId, e);
        }
    }

    @Override
    public boolean deleteMessage(String email, String folder, String messageId) throws MailStorageException {
        try {
            Path emlPath = getFolderPath(email, folder).resolve(messageId + CONTENT_EXTENSION);
            boolean deleted = Files.deleteIfExists(emlPath);

            if (deleted) {
                removeFromFolderIndex(email, folder, messageId);
                logger.info("Deleted message {} from {}/{}", messageId, email, folder);
            }

            return deleted;
        } catch (IOException e) {
            throw new MailStorageException("Failed to delete message: " + messageId, e);
        }
    }

    @Override
    public void updateFlags(String email, String folder, String messageId, Set<String> flags, boolean replace)
            throws MailStorageException {
        try {
            List<MessageMetadata> metadataList = loadFolderIndex(email, folder);
            MessageMetadata metadata = metadataList.stream()
                    .filter(m -> m.getMessageId().equals(messageId))
                    .findFirst()
                    .orElse(null);

            if (metadata == null) {
                throw new MailStorageException("Message not found: " + messageId);
            }

            if (replace) {
                metadata.setFlags(flags);
            } else {
                for (String flag : flags) {
                    metadata.addFlag(flag);
                }
            }

            saveFolderIndex(email, folder, metadataList);
        } catch (IOException e) {
            throw new MailStorageException("Failed to update flags", e);
        }
    }

    @Override
    public Set<String> listFolders(String email) throws MailStorageException {
        Mailbox mailbox = mailboxCache.get(email);
        if (mailbox == null) {
            throw new MailStorageException("Mailbox not found: " + email);
        }
        return mailbox.getFolders();
    }

    @Override
    public void createFolder(String email, String folderName) throws MailStorageException {
        try {
            Mailbox mailbox = mailboxCache.get(email);
            if (mailbox == null) {
                throw new MailStorageException("Mailbox not found: " + email);
            }

            mailbox.addFolder(folderName);
            Path folderPath = getFolderPath(email, folderName);
            Files.createDirectories(folderPath);

            // Update mailbox metadata
            saveMailboxMetadata(mailbox);
            logger.info("Created folder {}/{}", email, folderName);
        } catch (IOException e) {
            throw new MailStorageException("Failed to create folder: " + folderName, e);
        }
    }

    @Override
    public void deleteFolder(String email, String folderName) throws MailStorageException {
        try {
            Mailbox mailbox = mailboxCache.get(email);
            if (mailbox == null) {
                throw new MailStorageException("Mailbox not found: " + email);
            }

            mailbox.removeFolder(folderName);
            Path folderPath = getFolderPath(email, folderName);

            // Delete all files in folder
            if (Files.exists(folderPath)) {
                try (Stream<Path> files = Files.walk(folderPath)) {
                    files.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    logger.error("Failed to delete: " + path, e);
                                }
                            });
                }
            }

            // Update mailbox metadata
            saveMailboxMetadata(mailbox);
            logger.info("Deleted folder {}/{}", email, folderName);
        } catch (IOException e) {
            throw new MailStorageException("Failed to delete folder: " + folderName, e);
        }
    }

    @Override
    public int getMessageCount(String email, String folder) throws MailStorageException {
        try {
            List<MessageMetadata> metadataList = loadFolderIndex(email, folder);
            return metadataList.size();
        } catch (IOException e) {
            throw new MailStorageException("Failed to get message count", e);
        }
    }

    // Helper methods

    private Path getMailboxPath(String email) {
        return Paths.get(basePath, email);
    }

    private Path getFolderPath(String email, String folder) {
        return getMailboxPath(email).resolve(folder);
    }

    private Mailbox createMailbox(String email, String unusedParam) throws IOException {
        Mailbox mailbox = new Mailbox(email, email);
        Path mailboxPath = getMailboxPath(email);
        Files.createDirectories(mailboxPath);

        // Create standard folders
        for (String folder : mailbox.getFolders()) {
            Files.createDirectories(getFolderPath(email, folder));
        }

        // Save metadata
        saveMailboxMetadata(mailbox);

        logger.info("Created mailbox for user: {}", email);
        return mailbox;
    }

    private void saveMailboxMetadata(Mailbox mailbox) throws IOException {
        Path metaPath = getMailboxPath(mailbox.getEmail()).resolve(MAILBOX_META_FILE);
        Properties props = new Properties();
        props.setProperty("username", mailbox.getEmail());
        props.setProperty("email", mailbox.getEmail());
        props.setProperty("createdTime", mailbox.getCreatedTime().toString());
        props.setProperty("folders", String.join(",", mailbox.getFolders()));

        try (Writer writer = Files.newBufferedWriter(metaPath)) {
            props.store(writer, "Mailbox Metadata");
        }
    }

    private Mailbox loadMailbox(String email) throws IOException {
        Path metaPath = getMailboxPath(email).resolve(MAILBOX_META_FILE);
        Properties props = new Properties();

        try (Reader reader = Files.newBufferedReader(metaPath)) {
            props.load(reader);
        }

        String storedEmail = props.getProperty("email");
        String createdTimeStr = props.getProperty("createdTime");
        LocalDateTime createdTime = LocalDateTime.parse(createdTimeStr);
        String foldersStr = props.getProperty("folders", "INBOX,Sent,Drafts,Trash");
        Set<String> folders = new HashSet<>(Arrays.asList(foldersStr.split(",")));

        return new Mailbox(storedEmail, null, createdTime, folders);
    }

    private void loadExistingMailboxes() throws IOException {
        Path basePathObj = Paths.get(basePath);
        if (!Files.exists(basePathObj)) {
            return;
        }

        try (Stream<Path> paths = Files.list(basePathObj)) {
            paths.filter(Files::isDirectory)
                    .forEach(mailboxPath -> {
                        try {
                            String email = mailboxPath.getFileName().toString();
                            if (Files.exists(mailboxPath.resolve(MAILBOX_META_FILE))) {
                                Mailbox mailbox = loadMailbox(email);
                                mailboxCache.put(email, mailbox);
                                logger.info("Loaded mailbox: {}", email);
                            }
                        } catch (IOException e) {
                            logger.error("Failed to load mailbox: " + mailboxPath, e);
                        }
                    });
        }
    }

    private String generateMessageId() {
        // Use timestamp (milliseconds) + random component for uniqueness
        // Format: MSG + yyyyMMdd + HHmmssSSS + 4-digit random
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DATE_FORMATTER);
        String time = now.format(DateTimeFormatter.ofPattern("HHmmssSSS"));
        int random = (int) (Math.random() * 10000);
        return MESSAGE_ID_PREFIX + date + time + String.format("%04d", random);
    }

    private void updateFolderIndex(String email, String folder, MailMessage message, String messageId)
            throws IOException {
        String cacheKey = email + "/" + folder;
        ReadWriteLock lock = indexLocks.computeIfAbsent(cacheKey, k -> new ReentrantReadWriteLock());

        lock.writeLock().lock();
        try {
            List<MessageMetadata> metadata = loadFolderIndex(email, folder);

            MessageMetadata newMeta = new MessageMetadata(
                    messageId,
                    message.getFrom(),
                    extractSubject(message.getData()),
                    LocalDateTime.now(),
                    (long) message.getData().length(),
                    new HashSet<>());
            metadata.add(newMeta);

            saveFolderIndex(email, folder, metadata);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void removeFromFolderIndex(String email, String folder, String messageId) throws IOException {
        List<MessageMetadata> metadataList = loadFolderIndex(email, folder);
        metadataList.removeIf(m -> m.getMessageId().equals(messageId));
        saveFolderIndex(email, folder, metadataList);
    }

    private List<MessageMetadata> loadFolderIndex(String email, String folder) throws IOException {
        String cacheKey = email + "/" + folder;

        // Get or create lock for this folder
        ReadWriteLock lock = indexLocks.computeIfAbsent(cacheKey, k -> new ReentrantReadWriteLock());

        // Try reading from cache first
        lock.readLock().lock();
        try {
            List<MessageMetadata> cached = indexCache.get(cacheKey);
            if (cached != null) {
                logger.debug("Index cache hit for {}", cacheKey);
                return new ArrayList<>(cached); // Return copy for safety
            }
        } finally {
            lock.readLock().unlock();
        }

        // Cache miss - load from disk with write lock
        lock.writeLock().lock();
        try {
            // Double-check cache (another thread might have loaded it)
            List<MessageMetadata> cached = indexCache.get(cacheKey);
            if (cached != null) {
                return new ArrayList<>(cached);
            }

            // Load from disk
            Path indexPath = getFolderPath(email, folder).resolve(FOLDER_INDEX_FILE);
            List<MessageMetadata> metadata = new ArrayList<>();

            if (Files.exists(indexPath)) {
                List<String> lines = Files.readAllLines(indexPath);
                for (String line : lines) {
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split("\\|", -1);
                    if (parts.length >= 6) {
                        MessageMetadata meta = new MessageMetadata(
                                parts[0], // messageId
                                parts[1], // from
                                parts[2], // subject
                                LocalDateTime.parse(parts[3]), // receivedTime
                                Long.parseLong(parts[4]), // size
                                new HashSet<>(Arrays.asList(parts[5].split(","))) // flags
                        );
                        metadata.add(meta);
                    }
                }
            }

            // Cache it
            indexCache.put(cacheKey, new ArrayList<>(metadata));
            logger.debug("Loaded and cached index for {} ({} messages)", cacheKey, metadata.size());

            return metadata;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveFolderIndex(String email, String folder, List<MessageMetadata> metadataList)
            throws IOException {
        String cacheKey = email + "/" + folder;
        ReadWriteLock lock = indexLocks.computeIfAbsent(cacheKey, k -> new ReentrantReadWriteLock());

        lock.writeLock().lock();
        try {
            Path indexPath = getFolderPath(email, folder).resolve(FOLDER_INDEX_FILE);
            List<String> lines = metadataList.stream()
                    .map(meta -> String.format("%s|%s|%s|%d|%s|%s",
                            meta.getMessageId(),
                            meta.getFrom(),
                            meta.getSubject(),
                            meta.getSize(),
                            meta.getReceivedTime().toString(), // Changed to getReceivedTime()
                            String.join(",", meta.getFlags())))
                    .collect(Collectors.toList());

            Files.write(indexPath, lines, StandardCharsets.UTF_8);

            // Update cache
            indexCache.put(cacheKey, new ArrayList<>(metadataList));
            logger.debug("Saved and cached index for {} ({} messages)", cacheKey, metadataList.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private MailMessage loadMessage(Path emlPath, MessageMetadata metadata) throws IOException {
        String data = new String(Files.readAllBytes(emlPath), StandardCharsets.UTF_8);

        // Reconstruct recipients from metadata (we'll need to store this in index too)
        // For now, use empty list or extract from headers
        List<String> recipients = extractRecipients(data);

        MailMessage message = new MailMessage(
                metadata.getMessageId(),
                metadata.getFrom(),
                recipients,
                data,
                metadata.getReceivedTime());

        message.setFlags(metadata.getFlags());
        return message;
    }

    private String extractSubject(String data) {
        try {
            String[] lines = data.split("\r?\n");
            for (String line : lines) {
                if (line.toLowerCase().startsWith("subject:")) {
                    return line.substring(8).trim();
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract subject", e);
        }
        return "(no subject)";
    }

    private List<String> extractRecipients(String data) {
        List<String> recipients = new ArrayList<>();
        try {
            String[] lines = data.split("\r?\n");
            for (String line : lines) {
                if (line.toLowerCase().startsWith("to:")) {
                    String toLine = line.substring(3).trim();
                    // Simple parsing - just split by comma
                    String[] addrs = toLine.split(",");
                    for (String addr : addrs) {
                        recipients.add(addr.trim());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract recipients", e);
        }
        return recipients;
    }
}
