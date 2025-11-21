package com.email.server.command;

import com.email.server.session.SmtpSession;
import com.email.server.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeloCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(HeloCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        String[] parts = commandLine.split("\\s+", 2);

        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            session.sendResponse("501 Syntax: HELO hostname");
            return;
        }

        String hostname = parts[1].trim();
        session.setClientHostname(hostname);
        session.setState(SessionState.CONNECTED);

        logger.debug("HELO from {} at session {}", hostname, session.getSessionId());
        session.sendResponse("250 " + session.getServerName() + " Hello " + hostname);
    }

    @Override
    public boolean requiresAuth() {
        return false;
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }
}