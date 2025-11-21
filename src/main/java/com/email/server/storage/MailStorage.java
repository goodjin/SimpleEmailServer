package com.email.server.storage;

import java.util.List;
import java.util.Set;

public interface MailStorage {
    /**
     * Save mail to storage
     * 
     * @param from       Sender email address
     * @param recipients List of recipient email addresses
     * @param mailData   Raw mail data including headers and body
     * @return Message ID for the saved mail
     * @throws MailStorageException if storage fails
     */
    String saveMail(String from, List<String> recipients, String mailData) throws MailStorageException;

    /**
     * Retrieve mail by message ID
     * 
     * @param messageId Message ID
     * @return Mail data or null if not found
     * @throws MailStorageException if retrieval fails
     */
    MailMessage getMail(String messageId) throws MailStorageException;

    /**
     * Delete mail by message ID
     * 
     * @param messageId Message ID
     * @return true if mail was deleted, false if not found
     * @throws MailStorageException if deletion fails
     */
    boolean deleteMail(String messageId) throws MailStorageException;

    /**
     * Check if storage is available
     * 
     * @return true if storage is available
     */
    boolean isAvailable();

    /**
     * Get storage statistics
     * 
     * @return Storage statistics
     */
    StorageStats getStats();

    /**
     * Initialize storage
     * 
     * @throws MailStorageException if initialization fails
     */
    void initialize() throws MailStorageException;

    /**
     * Shutdown storage
     */
    void shutdown();

    /**
     * Retrieve messages for a specific user
     * 
     * @param username Username or email address
     * @return List of messages for the user
     * @throws MailStorageException if retrieval fails
     */
    List<MailMessage> getMessagesForUser(String username) throws MailStorageException;

    /**
     * Update flags for a message
     * 
     * @param messageId Message ID
     * @param flags     New flags
     * @param replace   If true, replace existing flags; otherwise add/remove based
     *                  on flag content
     * @throws MailStorageException if update fails
     */
    void updateFlags(String messageId, Set<String> flags, boolean replace) throws MailStorageException;
}