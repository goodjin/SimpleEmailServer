package com.email.server.command;

import com.email.server.session.SmtpSession;
import com.email.server.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(DataCommandHandler.class);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        if (session.getState() != SessionState.TRANSACTION) {
            session.sendResponse("503 Bad sequence of commands");
            return;
        }

        if (session.getRcptTo().isEmpty()) {
            session.sendResponse("503 No recipients");
            return;
        }

        session.setState(SessionState.DATA);
        session.sendResponse("354 Start mail input; end with \u003cCRLF\u003e.\u003cCRLF\u003e");

        logger.debug("DATA command received at session {}", session.getSessionId());
    }

    @Override
    public boolean requiresAuth() {
        return false;
    }

    @Override
    public boolean requiresTransaction() {
        return true;
    }
}