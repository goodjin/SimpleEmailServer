package com.email.server.command;

import com.email.server.session.SmtpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpnCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExpnCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        logger.debug("EXPN command at session {}", session.getSessionId());
        // For security reasons, most SMTP servers don't implement EXPN
        session.sendResponse("502 Command not implemented");
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