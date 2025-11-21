package com.email.server.command;

import com.email.server.session.SmtpSession;
import com.email.server.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailCommandHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(MailCommandHandler.class);
    private static final Pattern MAIL_PATTERN = Pattern.compile("MAIL\\s+FROM:\\s*<(.+?)>", Pattern.CASE_INSENSITIVE);

    @Override
    public void handle(SmtpSession session, String commandLine) {
        if (session.getState() != SessionState.CONNECTED) {
            session.sendResponse("503 Bad sequence of commands");
            return;
        }

        Matcher matcher = MAIL_PATTERN.matcher(commandLine);
        if (!matcher.matches()) {
            session.sendResponse("501 Syntax: MAIL FROM:\u003caddress\u003e");
            return;
        }

        String fromAddress = matcher.group(1);
        if (!isValidEmail(fromAddress)) {
            session.sendResponse("501 Invalid email address");
            return;
        }

        // Reset transaction state
        session.resetTransaction();
        session.setMailFrom(fromAddress);
        session.setState(SessionState.TRANSACTION);

        logger.debug("MAIL FROM: {} at session {}", fromAddress, session.getSessionId());
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
        return false;
    }
}