package com.email.server.command;

import com.email.server.session.SmtpSession;
import com.email.server.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EhloCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(EhloCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        String[] parts = commandLine.split("\\s+", 2);

        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            session.sendResponse("501 Syntax: EHLO hostname");
            return;
        }

        String hostname = parts[1].trim();
        session.setClientHostname(hostname);
        session.setState(SessionState.CONNECTED);

        logger.debug("EHLO from {} at session {}", hostname, session.getSessionId());

        // Send EHLO response with supported extensions
        session.sendResponse("250-" + session.getServerName() + " Hello " + hostname);
        session.sendResponse("250-SIZE 10485760"); // 10MB max message size
        session.sendResponse("250-PIPELINING");
        session.sendResponse("250-ENHANCEDSTATUSCODES");
        session.sendResponse("250-8BITMIME");
        session.sendResponse("250 SMTPUTF8");
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