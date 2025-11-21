package com.email.server.command;

import java.util.HashMap;
import java.util.Map;

public class CommandHandlerFactory {
    private final Map<SmtpCommand, CommandHandler> handlers;

    public CommandHandlerFactory() {
        this.handlers = new HashMap<>();
        initializeHandlers();
    }

    private void initializeHandlers() {
        handlers.put(SmtpCommand.HELO, new HeloCommandHandler());
        handlers.put(SmtpCommand.EHLO, new EhloCommandHandler());
        handlers.put(SmtpCommand.MAIL, new MailCommandHandler());
        handlers.put(SmtpCommand.RCPT, new RcptCommandHandler());
        handlers.put(SmtpCommand.DATA, new DataCommandHandler());
        handlers.put(SmtpCommand.RSET, new RsetCommandHandler());
        handlers.put(SmtpCommand.NOOP, new NoopCommandHandler());
        handlers.put(SmtpCommand.QUIT, new QuitCommandHandler());
        handlers.put(SmtpCommand.VRFY, new VrfyCommandHandler());
        handlers.put(SmtpCommand.EXPN, new ExpnCommandHandler());
        handlers.put(SmtpCommand.HELP, new HelpCommandHandler());
    }

    public CommandHandler getHandler(SmtpCommand command) {
        return handlers.get(command);
    }
}