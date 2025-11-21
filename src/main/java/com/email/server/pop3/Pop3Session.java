package com.email.server.pop3;

import com.email.server.storage.MailMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Pop3Session {
    public enum State {
        AUTHORIZATION,
        TRANSACTION,
        UPDATE
    }

    private State state = State.AUTHORIZATION;
    private String username;
    private List<MailMessage> messages;
    private Set<Integer> deletedMessageIndices = new HashSet<>();

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<MailMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<MailMessage> messages) {
        this.messages = messages;
    }

    public void markDeleted(int index) {
        deletedMessageIndices.add(index);
    }

    public void unmarkDeleted(int index) {
        deletedMessageIndices.remove(index);
    }

    public void resetDeleted() {
        deletedMessageIndices.clear();
    }

    public boolean isDeleted(int index) {
        return deletedMessageIndices.contains(index);
    }

    public Set<Integer> getDeletedMessageIndices() {
        return new HashSet<>(deletedMessageIndices);
    }
}
