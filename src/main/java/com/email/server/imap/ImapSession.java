package com.email.server.imap;

import com.email.server.storage.MailMessage;

import java.util.List;

public class ImapSession {
    public enum State {
        NOT_AUTHENTICATED,
        AUTHENTICATED,
        SELECTED,
        LOGOUT
    }

    private State state = State.NOT_AUTHENTICATED;
    private String username;
    private String selectedMailbox;
    private List<MailMessage> messages;

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

    public String getSelectedMailbox() {
        return selectedMailbox;
    }

    public void setSelectedMailbox(String selectedMailbox) {
        this.selectedMailbox = selectedMailbox;
    }

    public List<MailMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<MailMessage> messages) {
        this.messages = messages;
    }
}
