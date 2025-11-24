package com.email.server.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Simple SMTP client for sending emails to external mail servers
 */
public class ExternalSmtpClient {
    private static final Logger logger = LoggerFactory.getLogger(ExternalSmtpClient.class);
    private static final int DEFAULT_SMTP_PORT = 25;
    private static final int READ_TIMEOUT = 30000; // 30 seconds

    public boolean sendEmail(String mxHost, String from, String to, String data) {
        return sendEmail(mxHost, DEFAULT_SMTP_PORT, from, to, data);
    }

    public boolean sendEmail(String mxHost, int port, String from, String to, String data) {
        Socket socket = null;
        BufferedReader reader = null;
        BufferedWriter writer = null;

        try {
            logger.info("Connecting to MX host: {}:{}", mxHost, port);
            socket = new Socket(mxHost, port);
            socket.setSoTimeout(READ_TIMEOUT);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Read greeting
            String response = readResponse(reader);
            if (!response.startsWith("220")) {
                logger.error("Unexpected greeting from {}: {}", mxHost, response);
                return false;
            }

            // Send EHLO
            sendCommand(writer, "EHLO localhost");
            response = readResponse(reader);
            if (!response.startsWith("250")) {
                logger.error("EHLO failed: {}", response);
                return false;
            }

            // Send MAIL FROM
            sendCommand(writer, "MAIL FROM:<" + from + ">");
            response = readResponse(reader);
            if (!response.startsWith("250")) {
                logger.error("MAIL FROM failed: {}", response);
                return false;
            }

            // Send RCPT TO
            sendCommand(writer, "RCPT TO:<" + to + ">");
            response = readResponse(reader);
            if (!response.startsWith("250")) {
                logger.error("RCPT TO failed: {}", response);
                return false;
            }

            // Send DATA
            sendCommand(writer, "DATA");
            response = readResponse(reader);
            if (!response.startsWith("354")) {
                logger.error("DATA command failed: {}", response);
                return false;
            }

            // Send message data
            String[] lines = data.split("\r?\n");
            for (String line : lines) {
                // Dot-stuffing: if line starts with '.', prepend another '.'
                if (line.startsWith(".")) {
                    writer.write(".");
                }
                writer.write(line);
                writer.write("\r\n");
            }
            writer.write(".\r\n");
            writer.flush();

            response = readResponse(reader);
            if (!response.startsWith("250")) {
                logger.error("Message delivery failed: {}", response);
                return false;
            }

            // Send QUIT
            sendCommand(writer, "QUIT");

            logger.info("Successfully delivered email from {} to {} via {}", from, to, mxHost);
            return true;

        } catch (Exception e) {
            logger.error("Error delivering email to {}: {}", mxHost, e.getMessage());
            return false;
        } finally {
            try {
                if (reader != null)
                    reader.close();
                if (writer != null)
                    writer.close();
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                logger.warn("Error closing connection: {}", e.getMessage());
            }
        }
    }

    private void sendCommand(BufferedWriter writer, String command) throws IOException {
        logger.debug("SMTP >>> {}", command);
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
    }

    private String readResponse(BufferedReader reader) throws IOException {
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
            logger.debug("SMTP <<< {}", line);

            // Multi-line responses have "-" after code, single line has space
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }

        return response.toString().trim();
    }
}
