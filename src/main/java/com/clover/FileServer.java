package com.clover;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service("fileServer")
public class FileServer {
    private static Logger logger = Logger.getLogger(FileServer.class);

    private NioEventLoopGroup bossGroup = new NioEventLoopGroup();
    private NioEventLoopGroup workGroup = new NioEventLoopGroup();

    @Value("${port}")
    public int port;

    @Value("${sslPort}")
    public int sslPort;

    @Autowired
    private FileInitializer fileInitializer;

    @Autowired
    private HttpsFileInitializer httpsFileInitializer;

    @PostConstruct
    public void start() {
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childHandler(fileInitializer);
            bootstrap.bind(port);

            //Https
            ServerBootstrap sslBootstrap = new ServerBootstrap();
            sslBootstrap.group(bossGroup, workGroup);
            sslBootstrap.channel(NioServerSocketChannel.class);
            sslBootstrap.childHandler(httpsFileInitializer);
            sslBootstrap.bind(sslPort);

            System.out.println("started file server...");
        }
        catch (Exception e) {
            LogUtil.error(logger, e);
        }
    }

    public static void main(String[] args) {
        // spring管理
        ApplicationContext context = new ClassPathXmlApplicationContext("spring-mybatis.xml");
    }
}
