package com.email.server.pop3;

import com.email.server.storage.MailMessage;
import com.email.server.mailbox.MailboxStorage;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Pop3Handler extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(Pop3Handler.class);

    private final MailboxStorage mailboxStorage;
    private final Pop3Session session;

    public Pop3Handler(MailboxStorage mailboxStorage) {
        this.mailboxStorage = mailboxStorage;
        this.session = new Pop3Session();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush("+OK POP3 server ready\r\n");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        String[] parts = msg.trim().split("\\s+", 2);
        String commandName = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        Pop3Command command = Pop3Command.fromString(commandName);

        try {
            handleCommand(ctx, command, args);
        } catch (Exception e) {
            logger.error("Error handling command: " + command, e);
            ctx.writeAndFlush("-ERR Internal server error\r\n");
        }
    }

    private void handleCommand(ChannelHandlerContext ctx, Pop3Command command, String args) throws Exception {
        switch (command) {
            case USER:
                handleUser(ctx, args);
                break;
            case PASS:
                handlePass(ctx, args);
                break;
            case STAT:
                handleStat(ctx);
                break;
            case LIST:
                handleList(ctx, args);
                break;
            case RETR:
                handleRetr(ctx, args);
                break;
            case DELE:
                handleDele(ctx, args);
                break;
            case NOOP:
                ctx.writeAndFlush("+OK\r\n");
                break;
            case RSET:
                handleRset(ctx);
                break;
            case QUIT:
                handleQuit(ctx);
                break;
            case CAPA:
                handleCapa(ctx);
                break;
            case UIDL:
                handleUidl(ctx, args);
                break;
            default:
                ctx.writeAndFlush("-ERR Unknown command\r\n");
        }
    }

    private void handleUser(ChannelHandlerContext ctx, String username) {
        if (session.getState() != Pop3Session.State.AUTHORIZATION) {
            ctx.writeAndFlush("-ERR Command not allowed in current state\r\n");
            return;
        }
        // Store full email address as username
        session.setUsername(username);
        ctx.writeAndFlush("+OK User accepted\r\n");
    }

    private void handlePass(ChannelHandlerContext ctx, String password) {
        if (session.getState() != Pop3Session.State.AUTHORIZATION) {
            ctx.writeAndFlush("-ERR Command not allowed in current state\r\n");
            return;
        }
        if (session.getUsername() == null) {
            ctx.writeAndFlush("-ERR USER command required first\r\n");
            return;
        }

        // Use full email for mailbox lookup
        try {
            List<MailMessage> messages = mailboxStorage.getMessages(session.getUsername(), "INBOX");
            session.setMessages(messages);
            session.setState(Pop3Session.State.TRANSACTION);
            ctx.writeAndFlush("+OK Mailbox locked and ready\r\n");
        } catch (Exception e) {
            logger.error("Error loading messages for user: " + session.getUsername(), e);
            ctx.writeAndFlush("-ERR Authentication failed\r\n");
        }
    }

    private void handleStat(ChannelHandlerContext ctx) {
        if (session.getState() != Pop3Session.State.TRANSACTION) {
            ctx.writeAndFlush("-ERR Command not allowed in current state\r\n");
            return;
        }

        int count = 0;
        long size = 0;
        List<MailMessage> messages = session.getMessages();

        for (int i = 0; i < messages.size(); i++) {
            if (!session.isDeleted(i)) {
                count++;
                size += messages.get(i).getSize();
            }
        }

        ctx.writeAndFlush("+OK " + count + " " + size + "\r\n");
    }

    private void handleList(ChannelHandlerContext ctx, String args) {
        if (session.getState() != Pop3Session.State.TRANSACTION) {
            ctx.writeAndFlush("-ERR Command not allowed in current state\r\n");
            return;
        }

        List<MailMessage> messages = session.getMessages();

        if (!args.isEmpty()) {
            try {
                int msgNum = Integer.parseInt(args);
                int index = msgNum - 1;
                if (index >= 0 && index < messages.size() && !session.isDeleted(index)) {
                    ctx.writeAndFlush("+OK " + msgNum + " " + messages.get(index).getSize() + "\r\n");
                } else {
                    ctx.writeAndFlush("-ERR No such message\r\n");
                }
            } catch (NumberFormatException e) {
                ctx.writeAndFlush("-ERR Invalid message number\r\n");
            }
            return;
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        long size = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (!session.isDeleted(i)) {
                count++;
                size += messages.get(i).getSize();
            }
        }
        sb.append("+OK ").append(count).append(" messages (").append(size).append(" octets)\r\n");

        for (int i = 0; i < messages.size(); i++) {
            if (!session.isDeleted(i)) {
                sb.append(i + 1).append(" ").append(messages.get(i).getSize()).append("\r\n");
            }
        }
        sb.append(".\r\n");
        ctx.writeAndFlush(sb.toString());
    }

    private void handleRetr(ChannelHandlerContext ctx, String args) {
        if (session.getState() != Pop3Session.State.TRANSACTION) {
            ctx.writeAndFlush("-ERR Command not allowed in current state\r\n");
            return;
        }

        try {
            int msgNum = Integer.parseInt(args);
            int index = msgNum - 1;
            List<MailMessage> messages = session.getMessages();

            if (index >= 0 && index < messages.size() && !session.isDeleted(index)) {
                MailMessage message = messages.get(index);
                ctx.writeAndFlush("+OK " + message.getSize() + " octets\r\n");
                ctx.writeAndFlush(message.getData() + "\r\n.\r\n");
            } else {
                ctx.writeAndFlush("-ERR No such message\r\n");
            }
        } catch (NumberFormatException e) {
            ctx.writeAndFlush("-ERR Invalid message number\r\n");
        }
    }

    private void handleDele(ChannelHandlerContext ctx, String args) {
        if (session.getState() != Pop3Session.State.TRANSACTION) {
            ctx.writeAndFlush("-ERR Command not allowed in current state\r\n");
            return;
        }

        try {
            int msgNum = Integer.parseInt(args);
            int index = msgNum - 1;
            List<MailMessage> messages = session.getMessages();

            if (index >= 0 && index < messages.size()) {
                if (session.isDeleted(index)) {
                    ctx.writeAndFlush("-ERR Message already deleted\r\n");
                } else {
                    session.markDeleted(index);
                    ctx.writeAndFlush("+OK Message " + msgNum + " deleted\r\n");
                }
            } else {
                ctx.writeAndFlush("-ERR No such message\r\n");
            }
        } catch (NumberFormatException e) {
            ctx.writeAndFlush("-ERR Invalid message number\r\n");
        }
    }

    private void handleRset(ChannelHandlerContext ctx) {
        if (session.getState() != Pop3Session.State.TRANSACTION) {
            ctx.writeAndFlush("-ERR Command not allowed in current state\r\n");
            return;
        }

        session.resetDeleted();
        ctx.writeAndFlush("+OK\r\n");
    }

    private void handleQuit(ChannelHandlerContext ctx) {
        if (session.getState() == Pop3Session.State.TRANSACTION) {
            session.setState(Pop3Session.State.UPDATE);
            List<MailMessage> messages = session.getMessages();
            String username = session.getUsername();
            for (int index : session.getDeletedMessageIndices()) {
                if (index >= 0 && index < messages.size()) {
                    try {
                        mailboxStorage.deleteMessage(username, "INBOX", messages.get(index).getMessageId());
                    } catch (Exception e) {
                        logger.error("Error deleting message: " + messages.get(index).getMessageId(), e);
                    }
                }
            }
        }
        ctx.writeAndFlush("+OK Bye\r\n").addListener(ChannelFutureListener.CLOSE);
    }

    private void handleCapa(ChannelHandlerContext ctx) {
        ctx.writeAndFlush("+OK Capability list follows\r\nUSER\r\nUIDL\r\nTOP\r\n.\r\n");
    }

    private void handleUidl(ChannelHandlerContext ctx, String args) {
        if (session.getState() != Pop3Session.State.TRANSACTION) {
            ctx.writeAndFlush("-ERR Command not allowed in current state\r\n");
            return;
        }

        List<MailMessage> messages = session.getMessages();

        if (!args.isEmpty()) {
            try {
                int msgNum = Integer.parseInt(args);
                int index = msgNum - 1;
                if (index >= 0 && index < messages.size() && !session.isDeleted(index)) {
                    ctx.writeAndFlush("+OK " + msgNum + " " + messages.get(index).getMessageId() + "\r\n");
                } else {
                    ctx.writeAndFlush("-ERR No such message\r\n");
                }
            } catch (NumberFormatException e) {
                ctx.writeAndFlush("-ERR Invalid message number\r\n");
            }
            return;
        }

        ctx.writeAndFlush("+OK\r\n");
        for (int i = 0; i < messages.size(); i++) {
            if (!session.isDeleted(i)) {
                ctx.writeAndFlush((i + 1) + " " + messages.get(i).getMessageId() + "\r\n");
            }
        }
        ctx.writeAndFlush(".\r\n");
    }
}
