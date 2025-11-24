package com.email.server.smtp;

import com.email.server.config.ServerConfig;
import com.email.server.user.InMemoryUserRepository;
import com.email.server.user.UserRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.Assert.*;

public class SmtpAuthTest {

    private SmtpServer server;
    private int port = 2525;
    public TemporaryFolder tempFolder = new TemporaryFolder();
    private UserRepository userRepository;

    @Before
    public void setUp() throws Exception {
        tempFolder.create();

        // Create config
        // System properties will be picked up by ServerConfig.load()
        System.setProperty("smtp.port", String.valueOf(port));
        System.setProperty("storage.mail-path", tempFolder.getRoot().getAbsolutePath());
        System.setProperty("server.name", "localhost");

        ServerConfig config = ServerConfig.load();

        userRepository = new InMemoryUserRepository();
        ((InMemoryUserRepository) userRepository).addUser("test@example.com", "password123");

        server = new SmtpServer(config, userRepository,
                new com.email.server.mailbox.LocalMailboxStorage(tempFolder.getRoot().getAbsolutePath()));
        new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Wait for server to start
        Thread.sleep(1000);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
        tempFolder.delete();
        System.clearProperty("smtp.port");
        System.clearProperty("storage.mail-path");
        System.clearProperty("server.name");
    }

    @Test
    public void testAuthLoginSuccess() throws Exception {
        try (Socket socket = new Socket("localhost", port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String greeting = in.readLine();
            assertTrue(greeting.startsWith("220 "));
            assertTrue(greeting.contains("ESMTP Service Ready"));
            // if any

            out.println("EHLO localhost");
            String line;
            boolean authCapFound = false;
            while ((line = in.readLine()) != null) {
                if (line.contains("AUTH LOGIN PLAIN")) {
                    authCapFound = true;
                }
                if (line.startsWith("250 "))
                    break;
            }
            assertTrue("Should advertise AUTH capability", authCapFound);

            out.println("AUTH LOGIN");
            assertEquals("334 VXNlcm5hbWU6", in.readLine()); // Username:

            out.println(Base64.getEncoder().encodeToString("test@example.com".getBytes()));
            assertEquals("334 UGFzc3dvcmQ6", in.readLine()); // Password:

            out.println(Base64.getEncoder().encodeToString("password123".getBytes()));
            assertEquals("235 Authentication successful", in.readLine());
        }
    }

    @Test
    public void testAuthPlainSuccess() throws Exception {
        try (Socket socket = new Socket("localhost", port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            in.readLine(); // Greeting
            out.println("EHLO localhost");
            while (!in.readLine().startsWith("250 "))
                ;

            String authString = "\0test@example.com\0password123";
            String base64Auth = Base64.getEncoder().encodeToString(authString.getBytes());

            out.println("AUTH PLAIN " + base64Auth);
            assertEquals("235 Authentication successful", in.readLine());
        }
    }

    @Test
    public void testAuthFailure() throws Exception {
        try (Socket socket = new Socket("localhost", port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            in.readLine(); // Greeting
            out.println("EHLO localhost");
            while (!in.readLine().startsWith("250 "))
                ;

            out.println("AUTH LOGIN");
            in.readLine(); // Username:
            out.println(Base64.getEncoder().encodeToString("wrong".getBytes()));
            in.readLine(); // Password:
            out.println(Base64.getEncoder().encodeToString("wrong".getBytes()));
            assertEquals("535 Authentication credentials invalid", in.readLine());
        }
    }

    @Test
    public void testMailSendWithAuthAndStorage() throws Exception {
        try (Socket socket = new Socket("localhost", port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            in.readLine(); // Greeting
            out.println("EHLO localhost");
            while (!in.readLine().startsWith("250 "))
                ;

            // Auth
            String authString = "\0test@example.com\0password123";
            String base64Auth = Base64.getEncoder().encodeToString(authString.getBytes());
            out.println("AUTH PLAIN " + base64Auth);
            in.readLine(); // Success

            // Send Mail
            out.println("MAIL FROM:<test@example.com>");
            assertEquals("250 OK", in.readLine());

            out.println("RCPT TO:<recipient@example.com>");
            assertEquals("250 OK", in.readLine());

            out.println("DATA");
            assertEquals("354 Start mail input; end with <CRLF>.<CRLF>", in.readLine());

            out.println("Subject: Test Email");
            out.println("");
            out.println("This is a test email body.");
            out.println(".");
            assertEquals("250 OK Message accepted for delivery", in.readLine());
        }

        // Verify storage
        // We need to check if files exist in tempFolder
        // Structure: year/month/messageId/messageId.eml

        // Since we don't know the message ID, we can walk the directory
        long count = Files.walk(tempFolder.getRoot().toPath())
                .filter(p -> p.toString().endsWith(".eml"))
                .count();
        assertEquals("Should have stored 1 email", 1, count);

        long metaCount = Files.walk(tempFolder.getRoot().toPath())
                .filter(p -> p.toString().endsWith(".meta"))
                .count();
        assertEquals("Should have stored 1 meta file", 1, metaCount);
    }
}
