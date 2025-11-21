package com.email.server.mailbox;

import com.email.server.storage.MailMessage;
import com.email.server.storage.MailStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class LocalMailboxStorage implements MailboxStorage {
    private static final Logger logger = LoggerFactory.getLogger(LocalMailboxStorage.class);
    private static final String MAILBOX_META_FILE = ".meta";
    private static final String FOLDER_INDEX_FILE = "index";
    private static final String MESSAGE_ID_PREFIX = "MSG";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String basePath;
    private final Map<String, Mailbox> mailboxCache = new ConcurrentHashMap<>();

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
            Path emlPath = folderPath.resolve(messageId + ".eml");
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
                Path emlPath = folderPath.resolve(metadata.getMessageId() + ".eml");
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
            Path emlPath = getFolderPath(email, folder).resolve(messageId + ".eml");
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
            Path emlPath = getFolderPath(email, folder).resolve(messageId + ".eml");
            boolean deleted = Files.deleteIfExists(emlPath);

            if (deleted) {
                removeFromFolderIndex(email, folder, messageId);
                logger.info("Deleted message {} from {}/{}", messageId, username, folder);
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
            logger.info("Created folder {}/{}", username, folderName);
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
            logger.info("Deleted folder {}/{}", username, folderName);
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
        Path metaPath = getMailboxPath(username).resolve(MAILBOX_META_FILE);
        Properties props = new Properties();

        try (Reader reader = Files.newBufferedReader(metaPath)) {
            props.load(reader);
        }

        String email = props.getProperty("email");
        String createdTimeStr = props.getProperty("createdTime");
        LocalDateTime createdTime = LocalDateTime.parse(createdTimeStr);
        String foldersStr = props.getProperty("folders", "INBOX,Sent,Drafts,Trash");
        Set<String> folders = new HashSet<>(Arrays.asList(foldersStr.split(",")));

        return new Mailbox(email, email, createdTime, folders);
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
        List<MessageMetadata> metadataList = loadFolderIndex(email, folder);

        // Extract subject from message data
        String subject = extractSubject(message.getData());

        MessageMetadata metadata = new MessageMetadata(
                messageId,
                message.getFrom(),
                subject,
                message.getReceivedTime(),
                message.getSize(),
                message.getFlags());

        metadataList.add(metadata);
        saveFolderIndex(email, folder, metadataList);
    }

    private void removeFromFolderIndex(String email, String folder, String messageId) throws IOException {
        List<MessageMetadata> metadataList = loadFolderIndex(email, folder);
        metadataList.removeIf(m -> m.getMessageId().equals(messageId));
        saveFolderIndex(email, folder, metadataList);
    }

    private List<MessageMetadata> loadFolderIndex(String email, String folder) throws IOException {
        Path indexPath = getFolderPath(email, folder).resolve(FOLDER_INDEX_FILE);
        if (!Files.exists(indexPath)) {
            return new ArrayList<>();
        }

        List<MessageMetadata> metadataList = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(indexPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                MessageMetadata metadata = parseIndexLine(line);
                if (metadata != null) {
                    metadataList.add(metadata);
                }
            }
        }

        return metadataList;
    }

    private void saveFolderIndex(String email, String folder, List<MessageMetadata> metadataList)
            throws IOException {
        Path indexPath = getFolderPath(email, folder).resolve(FOLDER_INDEX_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(indexPath)) {
            writer.write("# Message Index\n");
            writer.write("# Format: messageId|from|subject|receivedTime|size|flags\n");
            for (MessageMetadata metadata : metadataList) {
                writer.write(formatIndexLine(metadata));
                writer.write("\n");
            }
        }
    }

    private String formatIndexLine(MessageMetadata metadata) {
        return String.format("%s|%s|%s|%s|%d|%s",
                metadata.getMessageId(),
                metadata.getFrom(),
                metadata.getSubject(),
                metadata.getReceivedTime().toString(),
                metadata.getSize(),
                String.join(",", metadata.getFlags()));
    }

    private MessageMetadata parseIndexLine(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 5) {
                return null;
            }

            String messageId = parts[0];
            String from = parts[1];
            String subject = parts[2];
            LocalDateTime receivedTime = LocalDateTime.parse(parts[3]);
            long size = Long.parseLong(parts[4]);
            Set<String> flags = parts.length > 5 && !parts[5].isEmpty()
                    ? new HashSet<>(Arrays.asList(parts[5].split(",")))
                    : new HashSet<>();

            return new MessageMetadata(messageId, from, subject, receivedTime, size, flags);
        } catch (Exception e) {
            logger.error("Failed to parse index line: " + line, e);
            return null;
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
