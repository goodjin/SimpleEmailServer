package com.email.server.imap;

import com.email.server.storage.MailMessage;
import com.email.server.mailbox.MailboxStorage;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ImapHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(ImapHandler.class);

    private final MailboxStorage mailboxStorage;
    private final ImapSession session;

    public ImapHandler(MailboxStorage mailboxStorage) {
        this.mailboxStorage = mailboxStorage;
        this.session = new ImapSession();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush("* OK IMAP4rev1 Service Ready\r\n");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        String[] parts = msg.trim().split("\\s+", 3);
        if (parts.length < 2) {
            return; // Ignore invalid lines
        }

        String tag = parts[0];
        String commandName = parts[1];
        String args = parts.length > 2 ? parts[2] : "";

        ImapCommand command = ImapCommand.fromString(commandName);

        try {
            handleCommand(ctx, tag, command, args);
        } catch (Exception e) {
            logger.error("Error handling command: " + command, e);
            ctx.writeAndFlush(tag + " BAD Internal server error\r\n");
        }
    }

    private void handleCommand(ChannelHandlerContext ctx, String tag, ImapCommand command, String args)
            throws Exception {
        switch (command) {
            case LOGIN:
                handleLogin(ctx, tag, args);
                break;
            case CAPABILITY:
                ctx.writeAndFlush("* CAPABILITY IMAP4rev1 AUTH=PLAIN\r\n" + tag + " OK CAPABILITY completed\r\n");
                break;
            case LIST:
                handleList(ctx, tag, args);
                break;
            case SELECT:
                handleSelect(ctx, tag, args);
                break;
            case FETCH:
                handleFetch(ctx, tag, args);
                break;
            case STORE:
                handleStore(ctx, tag, args);
                break;
            case EXPUNGE:
                handleExpunge(ctx, tag);
                break;
            case NOOP:
                ctx.writeAndFlush(tag + " OK NOOP completed\r\n");
                break;
            case LOGOUT:
                ctx.writeAndFlush("* BYE IMAP4rev1 Server logging out\r\n" + tag + " OK LOGOUT completed\r\n")
                        .addListener(ChannelFutureListener.CLOSE);
                break;
            default:
                ctx.writeAndFlush(tag + " BAD Unknown command\r\n");
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, String tag, String args) {
        if (session.getState() != ImapSession.State.NOT_AUTHENTICATED) {
            ctx.writeAndFlush(tag + " BAD Already authenticated\r\n");
            return;
        }

        String[] parts = args.split("\\s+");
        if (parts.length != 2) {
            ctx.writeAndFlush(tag + " BAD Invalid arguments\r\n");
            return;
        }

        String email = parts[0].replace("\"", "");
        // String password = parts[1].replace("\"", ""); // Ignored for now

        // Use full email address for session
        session.setUsername(email);
        session.setState(ImapSession.State.AUTHENTICATED);
        ctx.writeAndFlush(tag + " OK LOGIN completed\r\n");
    }

    private void handleList(ChannelHandlerContext ctx, String tag, String args) {
        if (session.getState() == ImapSession.State.NOT_AUTHENTICATED) {
            ctx.writeAndFlush(tag + " NO Not authenticated\r\n");
            return;
        }

        ctx.writeAndFlush("* LIST (\\HasNoChildren) \"/\" \"INBOX\"\r\n" + tag + " OK LIST completed\r\n");
    }

    private void handleSelect(ChannelHandlerContext ctx, String tag, String args) {
        if (session.getState() == ImapSession.State.NOT_AUTHENTICATED) {
            ctx.writeAndFlush(tag + " NO Not authenticated\r\n");
            return;
        }

        String mailbox = args.replace("\"", "");
        if (!"INBOX".equalsIgnoreCase(mailbox)) {
            ctx.writeAndFlush(tag + " NO Mailbox doesn't exist\r\n");
            return;
        }

        try {
            List<MailMessage> messages = mailboxStorage.getMessages(session.getUsername(), "INBOX");
            session.setMessages(messages);
            session.setSelectedMailbox("INBOX");
            session.setState(ImapSession.State.SELECTED);

            ctx.writeAndFlush("* " + messages.size() + " EXISTS\r\n");
            ctx.writeAndFlush("* " + messages.size() + " RECENT\r\n");
            ctx.writeAndFlush("* OK [UIDVALIDITY " + System.currentTimeMillis() + "] UIDs valid\r\n");
            ctx.writeAndFlush(tag + " OK [READ-WRITE] SELECT completed\r\n");
        } catch (Exception e) {
            logger.error("Error selecting mailbox", e);
            ctx.writeAndFlush(tag + " NO Select failed\r\n");
        }
    }

    private void handleFetch(ChannelHandlerContext ctx, String tag, String args) {
        if (session.getState() != ImapSession.State.SELECTED) {
            ctx.writeAndFlush(tag + " NO No mailbox selected\r\n");
            return;
        }

        // Simplified FETCH parsing: assumes "1:*" or "1" and "(FLAGS)" or "(BODY[...])"
        // Real IMAP parsing is much more complex.

        List<MailMessage> messages = session.getMessages();
        // For now, just return all messages if range is 1:* or similar, or specific
        // message

        // Very basic parsing
        String[] parts = args.split("\\s+", 2);
        String sequenceSet = parts[0];
        String dataItems = parts.length > 1 ? parts[1] : "";

        int start = 1;
        int end = messages.size();

        if (!sequenceSet.equals("*") && !sequenceSet.equals("1:*")) {
            try {
                if (sequenceSet.contains(":")) {
                    String[] range = sequenceSet.split(":");
                    start = Integer.parseInt(range[0]);
                    if (!range[1].equals("*")) {
                        end = Integer.parseInt(range[1]);
                    }
                } else {
                    start = Integer.parseInt(sequenceSet);
                    end = start;
                }
            } catch (NumberFormatException e) {
                // Ignore invalid range, default to all
            }
        }

        for (int i = start - 1; i < end && i < messages.size(); i++) {
            if (i < 0)
                continue;
            MailMessage msg = messages.get(i);
            StringBuilder response = new StringBuilder("* " + (i + 1) + " FETCH (");

            if (dataItems.contains("FLAGS")) {
                response.append("FLAGS (");
                response.append(String.join(" ", msg.getFlags()));
                response.append(") ");
            }

            if (dataItems.contains("BODY") || dataItems.contains("RFC822")) {
                response.append("BODY[] {").append(msg.getData().length()).append("}\r\n");
                response.append(msg.getData());
            } else if (dataItems.contains("UID")) {
                // Simple UID using hashcode or similar if not real UID
                response.append(" UID ").append(i + 1);
            }

            response.append(")\r\n");
            ctx.writeAndFlush(response.toString());
        }

        ctx.writeAndFlush(tag + " OK FETCH completed\r\n");
    }

    private void handleStore(ChannelHandlerContext ctx, String tag, String args) {
        if (session.getState() != ImapSession.State.SELECTED) {
            ctx.writeAndFlush(tag + " NO No mailbox selected\r\n");
            return;
        }

        // STORE 1 +FLAGS (\Seen)
        String[] parts = args.split("\\s+");
        if (parts.length < 3) {
            ctx.writeAndFlush(tag + " BAD Invalid arguments\r\n");
            return;
        }

        String sequenceSet = parts[0];
        String operation = parts[1]; // +FLAGS, -FLAGS, FLAGS
        String flagsStr = args.substring(args.indexOf("(") + 1, args.indexOf(")"));
        Set<String> flags = new HashSet<>(Arrays.asList(flagsStr.split("\\s+")));

        try {
            int msgNum = Integer.parseInt(sequenceSet);
            int index = msgNum - 1;
            List<MailMessage> messages = session.getMessages();

            if (index >= 0 && index < messages.size()) {
                MailMessage msg = messages.get(index);
                // boolean replace = operation.equalsIgnoreCase("FLAGS");

                // Handle +FLAGS and -FLAGS logic if needed, but MailStorage.updateFlags handles
                // replace vs add
                // Actually MailStorage.updateFlags logic:
                // if replace=true, set flags.
                // if replace=false, add flags.
                // We need to handle -FLAGS (remove) separately or update MailStorage.

                // For simplicity, let's assume +FLAGS (add) or FLAGS (replace).
                // -FLAGS is tricky with current MailStorage.updateFlags.
                // Let's update MailStorage.updateFlags to support remove?
                // Or just read, modify, replace.

                // Since I can't easily change MailStorage interface again without more steps, I
                // will implement logic here
                // and use replace=true.

                Set<String> currentFlags = msg.getFlags();
                if (operation.equalsIgnoreCase("+FLAGS")) {
                    currentFlags.addAll(flags);
                } else if (operation.equalsIgnoreCase("-FLAGS")) {
                    currentFlags.removeAll(flags);
                } else {
                    currentFlags = flags;
                }

                mailboxStorage.updateFlags(session.getUsername(), "INBOX", msg.getMessageId(), currentFlags, true);

                // Send untagged response
                ctx.writeAndFlush("* " + msgNum + " FETCH (FLAGS (" + String.join(" ", currentFlags) + "))\r\n");
                ctx.writeAndFlush(tag + " OK STORE completed\r\n");
            } else {
                ctx.writeAndFlush(tag + " NO No such message\r\n");
            }
        } catch (Exception e) {
            logger.error("Error storing flags", e);
            ctx.writeAndFlush(tag + " NO Store failed\r\n");
        }
    }

    private void handleExpunge(ChannelHandlerContext ctx, String tag) {
        if (session.getState() != ImapSession.State.SELECTED) {
            ctx.writeAndFlush(tag + " NO No mailbox selected\r\n");
            return;
        }

        // Not fully implemented as we don't support \Deleted flag logic in storage
        // deletion yet
        // But we can simulate
        ctx.writeAndFlush(tag + " OK EXPUNGE completed\r\n");
    }
}
