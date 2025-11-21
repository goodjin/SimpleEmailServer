package com.email.server.command;

import com.email.server.session.SmtpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelpCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(HelpCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        logger.debug("HELP command at session {}", session.getSessionId());
        session.sendResponse("214-This server supports the following commands:");
        session.sendResponse("214 HELO EHLO MAIL RCPT DATA RSET NOOP QUIT VRFY EXPN HELP");
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