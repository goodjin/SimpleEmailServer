package com.email.server.session;

public enum SessionState {
    CONNECTED,      // After HELO/EHLO
    TRANSACTION,    // After MAIL FROM
    DATA,           // After DATA command
    CLOSED          // Connection closed
}