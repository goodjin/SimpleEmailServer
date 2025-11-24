package com.email.server.pop3;

import com.email.server.config.ServerConfig;
import com.email.server.mailbox.LocalMailboxStorage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class Pop3ServerTest {
    private Pop3Server server;
    private LocalMailboxStorage storage;
    private Path tempDir;
    private int port = 11110;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("mail-storage");

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("smtp.port", 2525);
        configMap.put("smtp.bind-address", "127.0.0.1");
        configMap.put("pop3.port", port);
        configMap.put("pop3.bind-address", "127.0.0.1");
        configMap.put("storage.mail-path", tempDir.toString());
        configMap.put("server.io-threads", 1);
        configMap.put("server.worker-threads", 2);
        configMap.put("server.max-connections", 10);
        configMap.put("server.connection-timeout", 30);
        configMap.put("server.name", "localhost");
        configMap.put("imap.port", 143);
        configMap.put("imap.bind-address", "127.0.0.1");

        Config config = ConfigFactory.parseMap(configMap);
        ServerConfig serverConfig = new ServerConfig(config);

        storage = new LocalMailboxStorage(tempDir.toString());
        storage.initialize();

        // Add a test message
        storage.saveMessage("user@example.com", "INBOX",
                new com.email.server.storage.MailMessage(null, "sender@example.com",
                        Collections.singletonList("user@example.com"), "Subject: Test\r\n\r\nHello World"));

        server = new Pop3Server(serverConfig, storage);
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (storage != null) {
            storage.shutdown();
        }
        // Cleanup temp dir (optional, OS handles it usually)
    }

    @Test
    public void testPop3Flow() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Read greeting
            String response = in.readLine();
            assertTrue(response.startsWith("+OK"));

            // CAPA
            out.println("CAPA");
            response = in.readLine();
            assertTrue(response.startsWith("+OK"));
            while (!(response = in.readLine()).equals(".")) {
                // read capabilities
            }

            // USER
            out.println("USER user@example.com");
            response = in.readLine();
            assertTrue(response.startsWith("+OK"));

            // PASS
            out.println("PASS password");
            response = in.readLine();
            assertTrue(response.startsWith("+OK"));

            // STAT
            out.println("STAT");
            response = in.readLine();
            assertTrue(response.startsWith("+OK 1")); // 1 message

            // LIST
            out.println("LIST");
            response = in.readLine();
            assertTrue(response.startsWith("+OK"));
            response = in.readLine();
            assertTrue(response.matches("1 \\d+")); // 1 size
            assertEquals(".", in.readLine());

            // RETR
            out.println("RETR 1");
            response = in.readLine();
            assertTrue(response.startsWith("+OK"));
            // Read body
            boolean foundBody = false;
            while (!(response = in.readLine()).equals(".")) {
                if (response.contains("Hello World")) {
                    foundBody = true;
                }
            }
            assertTrue(foundBody);

            // DELE
            out.println("DELE 1");
            response = in.readLine();
            assertTrue(response.startsWith("+OK"));

            // QUIT
            out.println("QUIT");
            response = in.readLine();
            assertTrue(response.startsWith("+OK"));
        }
    }
}
