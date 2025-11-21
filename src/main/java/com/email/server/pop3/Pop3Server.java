package com.email.server.pop3;

import com.email.server.config.ServerConfig;
import com.email.server.mailbox.MailboxStorage;
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

public class Pop3Server {
    private static final Logger logger = LoggerFactory.getLogger(Pop3Server.class);

    private final ServerConfig config;
    private final MailboxStorage mailboxStorage;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final io.netty.util.concurrent.EventExecutorGroup businessGroup;
    private ChannelFuture channelFuture;

    public Pop3Server(ServerConfig config, MailboxStorage mailboxStorage) {
        this.config = config;
        this.mailboxStorage = mailboxStorage;
        this.bossGroup = new NioEventLoopGroup(config.getIoThreads());
        this.workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        this.businessGroup = new io.netty.util.concurrent.DefaultEventExecutorGroup(config.getBusinessThreads());
    }

    public void start() throws InterruptedException {
        try {
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

                            // Add POP3 handler
                            pipeline.addLast(businessGroup, new Pop3Handler(mailboxStorage));
                        }
                    });

            // Bind and start to accept incoming connections
            channelFuture = bootstrap.bind(config.getPop3BindAddress(), config.getPop3Port()).sync();

            logger.info("POP3 server started on {}:{}", config.getPop3BindAddress(), config.getPop3Port());
        } catch (InterruptedException e) {
            logger.error("Failed to start POP3 server", e);
            throw e;
        }
    }

    public void stop() {
        logger.info("Stopping POP3 server...");

        try {
            if (channelFuture != null) {
                channelFuture.channel().close().sync();
            }
        } catch (InterruptedException e) {
            logger.error("Error closing server channel", e);
        }

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        businessGroup.shutdownGracefully();

        logger.info("POP3 server stopped");
    }
}
