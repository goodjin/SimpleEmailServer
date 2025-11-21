package com.email.server.pop3;

public enum Pop3Command {
    USER,
    PASS,
    STAT,
    LIST,
    RETR,
    DELE,
    NOOP,
    RSET,
    QUIT,
    CAPA,
    UIDL,
    UNKNOWN;

    public static Pop3Command fromString(String command) {
        try {
            return valueOf(command.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
