package com.email.server;

import com.email.server.config.ServerConfig;
import com.email.server.imap.ImapServer;
import com.email.server.pop3.Pop3Server;
import com.email.server.smtp.SmtpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmtpServerApplication {
    private static final Logger logger = LoggerFactory.getLogger(SmtpServerApplication.class);

    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.load();

            com.email.server.user.UserRepository userRepository = new com.email.server.user.InMemoryUserRepository();
            // Add default user for testing/demo
            ((com.email.server.user.InMemoryUserRepository) userRepository).addUser("user@example.com", "password");

            com.email.server.mailbox.MailboxStorage mailboxStorage = new com.email.server.mailbox.LocalMailboxStorage(
                    config.getMailStoragePath());

            SmtpServer smtpServer = new SmtpServer(config, userRepository, mailboxStorage);
            Pop3Server pop3Server = new Pop3Server(config, mailboxStorage);
            ImapServer imapServer = new ImapServer(config, mailboxStorage);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down servers...");
                imapServer.stop();
                pop3Server.stop();
                smtpServer.stop();
            }));

            smtpServer.start();
            pop3Server.start();
            imapServer.start();

            logger.info("SMTP server started on port {}", config.getSmtpPort());
            logger.info("POP3 server started on port {}", config.getPop3Port());
            logger.info("IMAP server started on port {}", config.getImapPort());

            // Keep the main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to start servers", e);
            System.exit(1);
        }
    }
}