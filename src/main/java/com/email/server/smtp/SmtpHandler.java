package com.email.server.smtp;

import com.email.server.config.ServerConfig;
import com.email.server.delivery.MailDeliveryService;
import com.email.server.session.SessionManager;
import com.email.server.session.SmtpSession;
import com.email.server.mailbox.MailboxStorage;
import com.email.server.storage.MailMessage;
import com.email.server.user.UserRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;

public class SmtpHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(SmtpHandler.class);

    private enum State {
        CONNECT,
        GREET,
        MAIL,
        RCPT,
        DATA,
        DATA_CONTENT,
        AUTH_LOGIN_USERNAME,
        AUTH_LOGIN_PASSWORD,
        QUIT
    }

    private final SessionManager sessionManager;
    private final MailboxStorage mailboxStorage;
    private final MailDeliveryService deliveryService;
    private final ServerConfig config;
    private final UserRepository userRepository;

    private SmtpSession session;
    private State state = State.CONNECT;
    private boolean authenticated = false;
    private String authUsername;

    public SmtpHandler(SessionManager sessionManager, MailboxStorage mailboxStorage,
            MailDeliveryService deliveryService, ServerConfig config,
            UserRepository userRepository) {
        this.sessionManager = sessionManager;
        this.mailboxStorage = mailboxStorage;
        this.deliveryService = deliveryService;
        this.config = config;
        this.userRepository = userRepository;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        session = sessionManager.createSession(ctx.channel(), config.getServerName());
        if (session == null) {
            ctx.writeAndFlush("421 Too many connections\r\n");
            ctx.close();
            return;
        }
        logger.info("New SMTP connection from {}", ctx.channel().remoteAddress());
        ctx.writeAndFlush("220 " + config.getServerName() + " ESMTP Service Ready\r\n");
        state = State.GREET;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (session != null) {
            sessionManager.removeSession(session.getSessionId());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        String line = msg.trim();

        if (state == State.DATA_CONTENT) {
            handleDataContent(ctx, msg); // Use raw msg to preserve whitespace
            return;
        }

        if (state == State.AUTH_LOGIN_USERNAME) {
            handleAuthLoginUsername(ctx, line);
            return;
        }

        if (state == State.AUTH_LOGIN_PASSWORD) {
            handleAuthLoginPassword(ctx, line);
            return;
        }

        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toUpperCase();
        String args = parts.length > 1 ? parts[1] : "";

        try {
            switch (command) {
                case "HELO":
                case "EHLO":
                    handleHelo(ctx, args);
                    break;
                case "AUTH":
                    handleAuth(ctx, args);
                    break;
                case "MAIL":
                    handleMail(ctx, args);
                    break;
                case "RCPT":
                    handleRcpt(ctx, args);
                    break;
                case "DATA":
                    handleData(ctx);
                    break;
                case "QUIT":
                    ctx.writeAndFlush("221 Bye\r\n");
                    ctx.close();
                    break;
                case "RSET":
                    resetState();
                    ctx.writeAndFlush("250 OK\r\n");
                    break;
                case "NOOP":
                    ctx.writeAndFlush("250 OK\r\n");
                    break;
                default:
                    ctx.writeAndFlush("500 Unknown command\r\n");
            }
        } catch (Exception e) {
            logger.error("Error handling command: " + command, e);
            ctx.writeAndFlush("451 Internal server error\r\n");
        }
    }

    private void handleHelo(ChannelHandlerContext ctx, String args) {
        session.setClientHostname(args);
        ctx.writeAndFlush("250-" + config.getServerName() + "\r\n250-AUTH LOGIN PLAIN\r\n250 OK\r\n");
    }

    private void handleAuth(ChannelHandlerContext ctx, String args) {
        if (authenticated) {
            ctx.writeAndFlush("503 Already authenticated\r\n");
            return;
        }

        String[] parts = args.split("\\s+");
        String mechanism = parts[0].toUpperCase();

        if ("LOGIN".equals(mechanism)) {
            state = State.AUTH_LOGIN_USERNAME;
            ctx.writeAndFlush("334 VXNlcm5hbWU6\r\n"); // Base64 encoded "Username:"
        } else if ("PLAIN".equals(mechanism)) {
            if (parts.length > 1) {
                handleAuthPlain(ctx, parts[1]);
            } else {
                ctx.writeAndFlush("501 Missing argument\r\n");
            }
        } else {
            ctx.writeAndFlush("504 Unrecognized authentication type\r\n");
        }
    }

    private void handleAuthPlain(ChannelHandlerContext ctx, String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            String authString = new String(decoded);
            // Format: [authorizationId] \0 authenticationId \0 passwd
            String[] parts = authString.split("\0");
            if (parts.length >= 3) {
                String username = parts[1];
                String password = parts[2];
                if (userRepository.validate(username, password)) {
                    authenticated = true;
                    authUsername = username;
                    ctx.writeAndFlush("235 Authentication successful\r\n");
                } else {
                    ctx.writeAndFlush("535 Authentication credentials invalid\r\n");
                }
            } else {
                ctx.writeAndFlush("501 Invalid arguments\r\n");
            }
        } catch (IllegalArgumentException e) {
            ctx.writeAndFlush("501 Invalid Base64\r\n");
        }
    }

    private void handleAuthLoginUsername(ChannelHandlerContext ctx, String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            authUsername = new String(decoded);
            state = State.AUTH_LOGIN_PASSWORD;
            ctx.writeAndFlush("334 UGFzc3dvcmQ6\r\n"); // Base64 encoded "Password:"
        } catch (IllegalArgumentException e) {
            state = State.GREET;
            ctx.writeAndFlush("501 Invalid Base64\r\n");
        }
    }

    private void handleAuthLoginPassword(ChannelHandlerContext ctx, String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            String password = new String(decoded);
            if (userRepository.validate(authUsername, password)) {
                authenticated = true;
                state = State.GREET;
                ctx.writeAndFlush("235 Authentication successful\r\n");
            } else {
                state = State.GREET;
                ctx.writeAndFlush("535 Authentication credentials invalid\r\n");
            }
        } catch (IllegalArgumentException e) {
            state = State.GREET;
            ctx.writeAndFlush("501 Invalid Base64\r\n");
        }
    }

    private void handleMail(ChannelHandlerContext ctx, String args) {
        if (!authenticated) {
            // Optional: Enforce auth
            ctx.writeAndFlush("530 Authentication required\r\n");
            return;
        }

        if (args.toUpperCase().startsWith("FROM:")) {
            String sender = args.substring(5).trim().replaceAll("[<>]", "");
            session.setMailFrom(sender);
            state = State.MAIL;
            ctx.writeAndFlush("250 OK\r\n");
        } else {
            ctx.writeAndFlush("501 Syntax error in parameters or arguments\r\n");
        }
    }

    private void handleRcpt(ChannelHandlerContext ctx, String args) {
        if (state != State.MAIL && state != State.RCPT) {
            ctx.writeAndFlush("503 Bad sequence of commands\r\n");
            return;
        }

        if (args.toUpperCase().startsWith("TO:")) {
            String recipient = args.substring(3).trim().replaceAll("[<>]", "");
            session.addRcptTo(recipient);
            state = State.RCPT;
            ctx.writeAndFlush("250 OK\r\n");
        } else {
            ctx.writeAndFlush("501 Syntax error in parameters or arguments\r\n");
        }
    }

    private void handleData(ChannelHandlerContext ctx) {
        if (state != State.RCPT) {
            ctx.writeAndFlush("503 Bad sequence of commands\r\n");
            return;
        }

        if (session.getRcptTo().isEmpty()) {
            ctx.writeAndFlush("554 No valid recipients\r\n");
            return;
        }

        state = State.DATA_CONTENT;
        session.setDataMode(true); // Update session state as well
        ctx.writeAndFlush("354 Start mail input; end with <CRLF>.<CRLF>\r\n");
    }

    private void handleDataContent(ChannelHandlerContext ctx, String line) {
        if (line.trim().equals(".")) {
            // End of data
            try {
                String data = session.getMailData();
                String sender = session.getMailFrom();
                List<String> recipients = session.getRcptTo();

                // Create MailMessage
                MailMessage message = new MailMessage(null, sender, recipients, data);

                // Save to each recipient's INBOX folder
                for (String recipient : recipients) {
                    try {
                        // Use full recipient email address for mailbox
                        String recipientEmail = recipient.replaceAll("[<>]", "");
                        mailboxStorage.saveMessage(recipientEmail, "INBOX", message);
                        logger.info("Message {} delivered to {}/INBOX", message.getMessageId(), recipientEmail);
                    } catch (Exception e) {
                        logger.error("Failed to deliver to " + recipient, e);
                    }
                }

                ctx.writeAndFlush("250 OK Message accepted for delivery\r\n");
                resetState();
            } catch (Exception e) {
                logger.error("Error saving mail", e);
                ctx.writeAndFlush("451 Local error in processing\r\n");
            }
        } else {
            // Handle dot-stuffing
            if (line.startsWith("..")) {
                session.appendMailData(line.substring(1));
            } else {
                session.appendMailData(line);
            }
        }
    }

    private void resetState() {
        state = State.GREET;
        session.resetTransaction();
    }
}