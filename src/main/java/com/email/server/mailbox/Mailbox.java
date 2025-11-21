package com.email.server.mailbox;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class Mailbox {
    private final String email;
    private final LocalDateTime createdTime;
    private final Set<String> folders;

    public Mailbox(String email, String emailDuplicate) {
        this.email = email;
        this.createdTime = LocalDateTime.now();
        this.folders = new HashSet<>();
        // Default folders
        folders.add("INBOX");
        folders.add("Sent");
        folders.add("Drafts");
        folders.add("Trash");
    }

    public Mailbox(String email, String emailDuplicate, LocalDateTime createdTime, Set<String> folders) {
        this.email = email;
        this.createdTime = createdTime;
        this.folders = folders;
    }

    public String getEmail() {
        return email;
    }

    // For backwards compatibility
    public String getUsername() {
        if (email.contains("@")) {
            return email.split("@")[0];
        }
        return email;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public Set<String> getFolders() {
        return new HashSet<>(folders);
    }

    public boolean hasFolder(String folderName) {
        return folders.contains(folderName);
    }

    public void addFolder(String folderName) {
        folders.add(folderName);
    }

    public void removeFolder(String folderName) {
        // Don't allow removing standard folders
        if (folderName.equals("INBOX") || folderName.equals("Sent") ||
                folderName.equals("Drafts") || folderName.equals("Trash")) {
            return;
        }
        folders.remove(folderName);
    }

    @Override
    public String toString() {
        return "Mailbox{username='" + username + "', email='" + email + "', folders=" + folders.size() + "}";
    }
}
