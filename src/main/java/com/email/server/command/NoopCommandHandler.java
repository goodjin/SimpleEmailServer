package com.email.server.command;

import com.email.server.session.SmtpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(NoopCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        logger.debug("NOOP command at session {}", session.getSessionId());
        session.sendResponse("250 OK");
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