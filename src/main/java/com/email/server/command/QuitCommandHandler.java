package com.email.server.command;

import com.email.server.session.SmtpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuitCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(QuitCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        logger.debug("QUIT command at session {}", session.getSessionId());
        session.sendResponse("221 " + session.getServerName() + " closing connection");
        session.close();
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