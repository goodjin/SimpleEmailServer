package com.email.server.mailbox;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class MessageMetadata {
    private final String messageId;
    private final String from;
    private final String subject;
    private final LocalDateTime receivedTime;
    private final long size;
    private Set<String> flags;

    public MessageMetadata(String messageId, String from, String subject, LocalDateTime receivedTime, long size) {
        this.messageId = messageId;
        this.from = from;
        this.subject = subject;
        this.receivedTime = receivedTime;
        this.size = size;
        this.flags = new HashSet<>();
    }

    public MessageMetadata(String messageId, String from, String subject, LocalDateTime receivedTime, long size,
            Set<String> flags) {
        this.messageId = messageId;
        this.from = from;
        this.subject = subject;
        this.receivedTime = receivedTime;
        this.size = size;
        this.flags = new HashSet<>(flags);
    }

    public String getMessageId() {
        return messageId;
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDateTime getReceivedTime() {
        return receivedTime;
    }

    public long getSize() {
        return size;
    }

    public Set<String> getFlags() {
        return new HashSet<>(flags);
    }

    public void setFlags(Set<String> flags) {
        this.flags = new HashSet<>(flags);
    }

    public void addFlag(String flag) {
        this.flags.add(flag);
    }

    public void removeFlag(String flag) {
        this.flags.remove(flag);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    @Override
    public String toString() {
        return "MessageMetadata{id='" + messageId + "', from='" + from + "', subject='" + subject + "', size=" + size
                + "}";
    }
}
