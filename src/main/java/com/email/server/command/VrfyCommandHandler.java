package com.email.server.command;

import com.email.server.session.SmtpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VrfyCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(VrfyCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        logger.debug("VRFY command at session {}", session.getSessionId());
        // For security reasons, most SMTP servers don't implement VRFY
        session.sendResponse("252 Cannot VRFY user, but will accept message and attempt delivery");
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