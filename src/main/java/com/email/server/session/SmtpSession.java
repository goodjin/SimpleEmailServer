package com.email.server.session;

import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class SmtpSession {
    private static final AtomicLong sessionCounter = new AtomicLong(0);

    private final String sessionId;
    private final Channel channel;
    private final String serverName;
    private final long createdTime;

    private volatile SessionState state = SessionState.CONNECTED;
    private String clientHostname;
    private String mailFrom;
    private final List<String> rcptTo = new ArrayList<>();
    private final StringBuilder mailData = new StringBuilder();
    private volatile boolean dataMode = false;

    public SmtpSession(Channel channel, String serverName) {
        this.sessionId = "session-" + sessionCounter.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        this.channel = channel;
        this.serverName = serverName;
        this.createdTime = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getServerName() {
        return serverName;
    }

    public SessionState getState() {
        return state;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public String getClientHostname() {
        return clientHostname;
    }

    public void setClientHostname(String clientHostname) {
        this.clientHostname = clientHostname;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public List<String> getRcptTo() {
        return new ArrayList<>(rcptTo);
    }

    public void addRcptTo(String recipient) {
        this.rcptTo.add(recipient);
    }

    public boolean isDataMode() {
        return dataMode;
    }

    public void setDataMode(boolean dataMode) {
        this.dataMode = dataMode;
    }

    public void appendMailData(String data) {
        mailData.append(data).append("\r\n");
    }

    public String getMailData() {
        return mailData.toString();
    }

    public void resetTransaction() {
        mailFrom = null;
        rcptTo.clear();
        mailData.setLength(0);
        dataMode = false;
    }

    public void sendResponse(String response) {
        if (channel.isActive()) {
            channel.writeAndFlush(response + "\r\n");
        }
    }

    public void close() {
        if (channel.isActive()) {
            channel.close();
        }
        state = SessionState.CLOSED;
    }

    public boolean isConnected() {
        return channel.isActive() && state != SessionState.CLOSED;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public long getIdleTime() {
        return System.currentTimeMillis() - createdTime;
    }

    public boolean isExpired() {
        return !isConnected() || getIdleTime() > 300000; // 5 minutes default
    }

    @Override
    public String toString() {
        return "SmtpSession{" +
                "sessionId='" + sessionId + '\'' +
                ", state=" + state +
                ", clientHostname='" + clientHostname + '\'' +
                ", mailFrom='" + mailFrom + '\'' +
                ", rcptTo=" + rcptTo +
                ", connected=" + isConnected() +
                '}';
    }
}