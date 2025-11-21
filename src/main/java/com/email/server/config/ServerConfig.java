package com.email.server.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ServerConfig {
    private final int smtpPort;
    private final String bindAddress;
    private final String mailStoragePath;
    private final String mailboxesPath;
    private final int ioThreads;
    private final int workerThreads;
    private final int businessThreads;
    private final int maxConnections;
    private final int connectionTimeout;
    private final String serverName;
    private final int pop3Port;
    private final String pop3BindAddress;
    private final int imapPort;
    private final String imapBindAddress;
    private final java.util.List<String> localDomains;
    private final Config config;

    public ServerConfig(Config config) {
        this.config = config;
        this.smtpPort = config.getInt("smtp.port");
        this.bindAddress = config.getString("smtp.bind-address");
        this.pop3Port = config.getInt("pop3.port");
        this.pop3BindAddress = config.getString("pop3.bind-address");
        this.imapPort = config.getInt("imap.port");
        this.imapBindAddress = config.getString("imap.bind-address");
        this.mailStoragePath = config.getString("storage.mail-path");
        this.mailboxesPath = config.hasPath("storage.mailboxes-path") ? config.getString("storage.mailboxes-path")
                : "./data/mailboxes";
        this.ioThreads = config.getInt("server.io-threads");
        this.workerThreads = config.getInt("server.worker-threads");
        this.businessThreads = config.hasPath("server.business-threads") ? config.getInt("server.business-threads")
                : 16;
        this.maxConnections = config.getInt("server.max-connections");
        this.connectionTimeout = config.getInt("server.connection-timeout");
        this.serverName = config.getString("server.name");
        this.localDomains = config.hasPath("domains.local")
                ? config.getStringList("domains.local")
                : java.util.Arrays.asList("localhost");
    }

    public static ServerConfig load() {
        Config config = ConfigFactory.load();
        return new ServerConfig(config);
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public String getMailStoragePath() {
        return mailStoragePath;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public int getBusinessThreads() {
        return businessThreads;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public String getServerName() {
        return serverName;
    }

    public int getPop3Port() {
        return pop3Port;
    }

    public String getPop3BindAddress() {
        return pop3BindAddress;
    }

    public int getImapPort() {
        return imapPort;
    }

    public String getImapBindAddress() {
        return imapBindAddress;
    }

    public String getMailboxesPath() {
        return mailboxesPath;
    }

    public Config getConfig() {
        return config;
    }

    public java.util.List<String> getLocalDomains() {
        return localDomains;
    }

    public boolean isLocalDomain(String domain) {
        return localDomains.contains(domain.toLowerCase());
    }
}