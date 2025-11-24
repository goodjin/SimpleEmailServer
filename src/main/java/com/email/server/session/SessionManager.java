package com.email.server.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, SmtpSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final int maxConnections;

    public SessionManager(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public SmtpSession createSession(io.netty.channel.Channel channel, String serverName) {
        if (activeSessions.get() >= maxConnections) {
            logger.warn("Maximum connections reached: {}", maxConnections);
            return null;
        }

        SmtpSession session = new SmtpSession(channel, serverName);
        sessions.put(session.getSessionId(), session);
        activeSessions.incrementAndGet();

        logger.info("Created new session: {} (active sessions: {})",
                session.getSessionId(), activeSessions.get());
        return session;
    }

    public void removeSession(String sessionId) {
        SmtpSession session = sessions.remove(sessionId);
        if (session != null) {
            activeSessions.decrementAndGet();
            logger.info("Removed session: {} (active sessions: {})",
                    sessionId, activeSessions.get());
        }
    }

    public SmtpSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public int getActiveSessionCount() {
        return activeSessions.get();
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void cleanupExpiredSessions(long maxIdleTime) {
        sessions.entrySet().removeIf(entry -> {
            SmtpSession session = entry.getValue();
            if (session.isExpired()) {
                logger.debug("Removing expired session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        logger.info("Shutting down session manager ({} active sessions)", activeSessions.get());

        // Close all active sessions
        for (SmtpSession session : sessions.values()) {
            try {
                session.close();
            } catch (Exception e) {
                logger.error("Error closing session: " + session.getSessionId(), e);
            }
        }

        sessions.clear();
        activeSessions.set(0);
    }
}