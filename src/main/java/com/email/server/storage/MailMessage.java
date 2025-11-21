package com.email.server.storage;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MailMessage {
    private final String messageId;
    private final String from;
    private final List<String> recipients;
    private final String data;
    private final LocalDateTime receivedTime;
    private final long size;
    private final Set<String> flags;

    public MailMessage(String messageId, String from, List<String> recipients, String data) {
        this(messageId, from, recipients, data, LocalDateTime.now());
    }

    public MailMessage(String messageId, String from, List<String> recipients, String data,
            LocalDateTime receivedTime) {
        this.messageId = messageId;
        this.from = from;
        this.recipients = recipients;
        this.data = data;
        this.receivedTime = receivedTime;
        this.size = data != null ? data.length() : 0;
        this.flags = new HashSet<>();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getFrom() {
        return from;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public String getData() {
        return data;
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
        this.flags.clear();
        if (flags != null) {
            this.flags.addAll(flags);
        }
    }

    @Override
    public String toString() {
        return "MailMessage{" +
                "messageId='" + messageId + '\'' +
                ", from='" + from + '\'' +
                ", recipients=" + recipients +
                ", receivedTime=" + receivedTime +
                ", size=" + size +
                '}';
    }
}