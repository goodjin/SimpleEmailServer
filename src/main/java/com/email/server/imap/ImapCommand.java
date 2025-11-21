package com.email.server.imap;

public enum ImapCommand {
    LOGIN,
    CAPABILITY,
    LIST,
    SELECT,
    FETCH,
    STORE,
    EXPUNGE,
    NOOP,
    LOGOUT,
    UNKNOWN;

    public static ImapCommand fromString(String command) {
        try {
            return valueOf(command.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
