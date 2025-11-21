package com.email.server.command;

import com.email.server.session.SmtpSession;
import com.email.server.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RcptCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(RcptCommandHandler.class);
    private static final Pattern RCPT_PATTERN = Pattern.compile("RCPT\\s+TO:\\s*<(.+?)>", Pattern.CASE_INSENSITIVE);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        if (session.getState() != SessionState.TRANSACTION) {
            session.sendResponse("503 Bad sequence of commands");
            return;
        }

        Matcher matcher = RCPT_PATTERN.matcher(commandLine);
        if (!matcher.matches()) {
            session.sendResponse("501 Syntax: RCPT TO:<address>");
            return;
        }

        String toAddress = matcher.group(1);
        if (!isValidEmail(toAddress)) {
            session.sendResponse("501 Invalid email address");
            return;
        }

        session.addRcptTo(toAddress);

        logger.debug("RCPT TO: {} at session {}", toAddress, session.getSessionId());
        session.sendResponse("250 OK");
    }

    private boolean isValidEmail(String email) {
        // Simple email validation - in production, use more robust validation
        return email != null && email.contains("@") && email.length() > 3;
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