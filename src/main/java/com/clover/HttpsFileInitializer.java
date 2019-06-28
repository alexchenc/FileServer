package com.clover;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.OptionalSslHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

@Service("httpsFileInitializer")
public class HttpsFileInitializer extends ChannelInitializer<SocketChannel> {

    @Autowired
    private FileHandler fileHandler;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        SSLEngine sslEngine = SSLContextFactory.getSSLContext().createSSLEngine();
        sslEngine.setUseClientMode(false);
        pipeline.addLast("SSL", new SslHandler(sslEngine));

        //字符解码
        pipeline.addLast("decoder", new HttpRequestDecoder());

        //字符编码
        pipeline.addLast("http-encoder", new HttpResponseEncoder());

        pipeline.addLast("http-chunked", new ChunkedWriteHandler());

        //逻辑处理
        pipeline.addLast("handler", fileHandler);
    }
}