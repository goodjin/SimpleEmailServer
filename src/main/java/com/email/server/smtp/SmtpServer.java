package com.email.server.smtp;

import com.email.server.config.ServerConfig;
import com.email.server.session.SessionManager;
import com.email.server.mailbox.MailboxStorage;
import com.email.server.mailbox.LocalMailboxStorage;
import com.email.server.delivery.MailDeliveryService;
import com.email.server.delivery.InMemoryDeliveryService;
import com.email.server.user.FileBasedUserRepository;
import com.email.server.user.UserRepository;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SmtpServer {
    private static final Logger logger = LoggerFactory.getLogger(SmtpServer.class);

    private final ServerConfig config;
    private final SessionManager sessionManager;
    private final MailboxStorage mailboxStorage;
    private final MailDeliveryService deliveryService;
    private final UserRepository userRepository;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final io.netty.util.concurrent.EventExecutorGroup businessGroup;
    private ChannelFuture channelFuture;

    public SmtpServer(ServerConfig config) {
        this.config = config;
        this.userRepository = new FileBasedUserRepository(config.getConfig());
        this.sessionManager = new SessionManager(config.getMaxConnections());
        this.mailboxStorage = new LocalMailboxStorage(config.getMailboxesPath());
        this.deliveryService = new InMemoryDeliveryService();
        this.bossGroup = new NioEventLoopGroup(config.getIoThreads());
        this.workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        this.businessGroup = new io.netty.util.concurrent.DefaultEventExecutorGroup(config.getBusinessThreads());
    }

    public MailboxStorage getMailboxStorage() {
        return mailboxStorage;
    }

    public void start() throws Exception {
        try {
            // Initialize storage
            mailboxStorage.initialize();

            // Set up delivery service with storage reference (will be updated later)
            // ((InMemoryDeliveryService) deliveryService).setMailStorage(mailboxStorage);

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // Add idle state handler for connection timeout
                            pipeline.addLast(new IdleStateHandler(
                                    config.getConnectionTimeout(), 0, 0, TimeUnit.SECONDS));

                            // Add frame decoder for line-based protocol
                            pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));

                            // Add string codec
                            pipeline.addLast(new StringDecoder());
                            pipeline.addLast(new StringEncoder());

                            // Add SMTP handler
                            pipeline.addLast(businessGroup,
                                    new SmtpHandler(sessionManager, mailboxStorage, deliveryService, config,
                                            userRepository));
                        }
                    });

            // Bind and start to accept incoming connections
            channelFuture = bootstrap.bind(config.getBindAddress(), config.getSmtpPort()).sync();

            // Start delivery service
            deliveryService.start();

            logger.info("SMTP server started on {}:{}", config.getBindAddress(), config.getSmtpPort());
        } catch (InterruptedException e) {
            logger.error("Failed to start SMTP server", e);
            throw e;
        }
    }

    public void stop() {
        logger.info("Stopping SMTP server...");

        try {
            if (channelFuture != null) {
                channelFuture.channel().close().sync();
            }
        } catch (InterruptedException e) {
            logger.error("Error closing server channel", e);
        }

        try {
            deliveryService.stop();
        } catch (Exception e) {
            logger.error("Error stopping delivery service", e);
        }

        try {
            mailboxStorage.shutdown();
        } catch (Exception e) {
            logger.error("Error shutting down mail storage", e);
        }

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        businessGroup.shutdownGracefully();

        logger.info("SMTP server stopped");
    }
}