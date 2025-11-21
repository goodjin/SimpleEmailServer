package com.email.server.command;

import com.email.server.session.SmtpSession;
import com.email.server.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RsetCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(RsetCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        session.resetTransaction();
        session.setState(SessionState.CONNECTED);

        logger.debug("RSET command at session {}", session.getSessionId());
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