package com.email.server.command;

import com.email.server.session.SmtpSession;

public interface CommandHandler {
    void handle(SmtpSession session, String commandLine);
    boolean requiresAuth();
    boolean requiresTransaction();
}