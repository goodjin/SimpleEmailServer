package com.email.server.command;

public enum SmtpCommand {
    HELO("HELO"),
    EHLO("EHLO"),
    MAIL("MAIL"),
    RCPT("RCPT"),
    DATA("DATA"),
    RSET("RSET"),
    VRFY("VRFY"),
    EXPN("EXPN"),
    HELP("HELP"),
    NOOP("NOOP"),
    QUIT("QUIT"),
    STARTTLS("STARTTLS"),
    AUTH("AUTH"),
    UNKNOWN("UNKNOWN");

    private final String command;

    SmtpCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public static SmtpCommand fromString(String command) {
        if (command == null || command.isEmpty()) {
            return UNKNOWN;
        }

        String upperCommand = command.toUpperCase();
        for (SmtpCommand smtpCommand : values()) {
            if (upperCommand.startsWith(smtpCommand.command)) {
                return smtpCommand;
            }
        }
        return UNKNOWN;
    }
}