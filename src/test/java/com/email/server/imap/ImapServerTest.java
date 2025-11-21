package com.email.server.imap;

import com.email.server.config.ServerConfig;
import com.email.server.storage.LocalMailStorage;
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

public class ImapServerTest {
    private ImapServer server;
    private LocalMailStorage storage;
    private Path tempDir;
    private int port = 11143;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("mail-storage-imap");

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("smtp.port", 2526);
        configMap.put("smtp.bind-address", "127.0.0.1");
        configMap.put("pop3.port", 11111);
        configMap.put("pop3.bind-address", "127.0.0.1");
        configMap.put("imap.port", port);
        configMap.put("imap.bind-address", "127.0.0.1");
        configMap.put("storage.mail-path", tempDir.toString());
        configMap.put("server.io-threads", 1);
        configMap.put("server.worker-threads", 2);
        configMap.put("server.max-connections", 10);
        configMap.put("server.connection-timeout", 30);
        configMap.put("server.name", "localhost");

        Config config = ConfigFactory.parseMap(configMap);
        ServerConfig serverConfig = new ServerConfig(config);

        storage = new LocalMailStorage(tempDir.toString());
        storage.initialize();

        // Add a test message
        storage.saveMail("sender@example.com", Collections.singletonList("user@example.com"),
                "Subject: Test\r\n\r\nHello World");

        server = new ImapServer(serverConfig, storage);
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
    }

    @Test
    public void testImapFlow() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Read greeting
            String response = in.readLine();
            assertTrue(response.startsWith("* OK"));

            // CAPABILITY
            out.println("A01 CAPABILITY");
            response = in.readLine();
            assertTrue(response.startsWith("* CAPABILITY"));
            response = in.readLine();
            assertTrue(response.startsWith("A01 OK"));

            // LOGIN
            out.println("A02 LOGIN \"user@example.com\" \"password\"");
            response = in.readLine();
            assertTrue(response.startsWith("A02 OK"));

            // LIST
            out.println("A03 LIST \"\" \"*\"");
            response = in.readLine();
            assertTrue(response.startsWith("* LIST"));
            response = in.readLine();
            assertTrue(response.startsWith("A03 OK"));

            // SELECT
            out.println("A04 SELECT INBOX");
            while (!(response = in.readLine()).startsWith("A04")) {
                assertTrue(response.startsWith("*"));
            }
            assertTrue(response.startsWith("A04 OK"));

            // FETCH
            out.println("A05 FETCH 1 (FLAGS BODY[TEXT])");
            response = in.readLine();
            assertTrue(response.startsWith("* 1 FETCH"));
            assertTrue(response.contains("BODY[]"));
            // Read body content
            while (!(response = in.readLine()).startsWith("A05")) {
                // consume body
            }
            assertTrue(response.startsWith("A05 OK"));

            // STORE
            out.println("A06 STORE 1 +FLAGS (\\Seen)");
            response = in.readLine();
            assertTrue(response.startsWith("* 1 FETCH"));
            assertTrue(response.contains("\\Seen"));
            response = in.readLine();
            assertTrue(response.startsWith("A06 OK"));

            // LOGOUT
            out.println("A07 LOGOUT");
            response = in.readLine();
            assertTrue(response.startsWith("* BYE"));
            response = in.readLine();
            assertTrue(response.startsWith("A07 OK"));
        }
    }
}
