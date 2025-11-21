package com.email.server.storage;

public class MailStorageException extends Exception {
    public MailStorageException(String message) {
        super(message);
    }

    public MailStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}