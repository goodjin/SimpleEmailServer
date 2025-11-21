package com.email.server.mailbox;

import com.email.server.storage.MailMessage;
import com.email.server.storage.MailStorageException;

import java.util.List;
import java.util.Set;

public interface MailboxStorage {
    /**
     * Initialize the mailbox storage
     */
    void initialize() throws MailStorageException;

    /**
     * Shutdown the mailbox storage
     */
    void shutdown();

    /**
     * Create or get a mailbox for a user
     */
    Mailbox getOrCreateMailbox(String username, String email) throws MailStorageException;

    /**
     * Save a message to a user's folder
     */
    String saveMessage(String username, String folder, MailMessage message) throws MailStorageException;

    /**
     * Get all messages in a folder
     */
    List<MailMessage> getMessages(String username, String folder) throws MailStorageException;

    /**
     * Get a specific message
     */
    MailMessage getMessage(String username, String folder, String messageId) throws MailStorageException;

    /**
     * Delete a message from a folder
     */
    boolean deleteMessage(String username, String folder, String messageId) throws MailStorageException;

    /**
     * Update message flags
     */
    void updateFlags(String username, String folder, String messageId, Set<String> flags, boolean replace)
            throws MailStorageException;

    /**
     * List all folders for a user
     */
    Set<String> listFolders(String username) throws MailStorageException;

    /**
     * Create a new folder
     */
    void createFolder(String username, String folderName) throws MailStorageException;

    /**
     * Delete a folder
     */
    void deleteFolder(String username, String folderName) throws MailStorageException;

    /**
     * Get message count in a folder
     */
    int getMessageCount(String username, String folder) throws MailStorageException;
}
