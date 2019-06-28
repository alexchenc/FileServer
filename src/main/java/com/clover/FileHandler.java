package com.clover;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.ssl.OptionalSslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;

@Service("fileHandler")
@Scope("prototype")
// 注解@Sharable，默认的4版本不能自动导入匹配的包，需要手动加入
// 地址是import io.netty.channel.ChannelHandler.Sharable;
@Sharable
public class FileHandler implements ChannelInboundHandler {

    private static Logger logger = Logger.getLogger(FileHandler.class);

    private static final String ROOT_PATH = System.getProperty("user.dir");
    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

    @Value("${rootPath}")
    public String rootPath;

    @Value("${port}")
    public int port;

    @Value("${sslPort}")
    public int sslPort;

    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {

    }

    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {

    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

    }

    private HttpRequest request = null;
    private HttpPostRequestDecoder decoder = null;
    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); //Disk

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            // Http Request Header
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                //Get请求，处理下载
                if (request.method() == HttpMethod.GET) {
                    handleDownload(ctx, msg);
                }
            }
        }
        catch (Exception e) {
            LogUtil.error(logger, e);
        }
    }

    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {

    }

    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

    }

    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {

    }

    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

    }

    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LogUtil.error(logger, cause);
        if (ctx.channel().isActive()) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 处理下载请求
     *
     * @param ctx
     * @param msg
     */
    public void handleDownload(ChannelHandlerContext ctx, Object msg) {
        try {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;

                //解析地址，获取文件路径
                String uri = request.uri();
                String path = parseUri(uri);
                if (path == null) {
                    sendError(ctx, HttpResponseStatus.FORBIDDEN);
                    return;
                }

                //校验路径
                File file = new File(path);
                logger.info("download path：" + path);

                //文件夹时列出文件清单
                if (file.isDirectory()) {
                    if (uri.endsWith("/")) {
                        sendListing(ctx, file);
                    } else {
                        sendRedirect(ctx, uri + "/");
                    }
                    return;
                }

                if (file.isHidden() || !file.exists()) {
                    sendError(ctx, HttpResponseStatus.NOT_FOUND);
                    return;
                }

                //读取文件
                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");

                //设置返回头
                long fileLength = randomAccessFile.length();
                HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                HttpUtil.setContentLength(response, fileLength);
                setContentTypeHeader(response, file);

                //是否保持连接
                if (HttpUtil.isKeepAlive(request)) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }
                ctx.write(response);    //写入缓冲区

                //发送
                ChannelFuture sendFileFuture = ctx.write(new ChunkedFile(randomAccessFile, 0, fileLength, 8192), ctx.newProgressivePromise());
                sendFileFuture.addListener(new ChannelProgressiveFutureListener() {

                    public void operationComplete(ChannelProgressiveFuture future)
                            throws Exception {

                    }

                    public void operationProgressed(ChannelProgressiveFuture future,
                                                    long progress, long total) throws Exception {
                    }
                });

                ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                if (!HttpUtil.isKeepAlive(request)) {
                    lastContentFuture.addListener(ChannelFutureListener.CLOSE);
                }
            }
        }
        catch (Exception e) {
            LogUtil.error(logger, e);
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 解析URI获取文件路径
     *
     * @param uri
     * @return
     */
    private String parseUri(String uri) {
        String path = null;
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error();
            }
        }

        if (uri.contains(File.separator + '.') || uri.contains('.' + File.separator) || uri.startsWith(".") || uri.endsWith(".")
                || INSECURE_URI.matcher(uri).matches()) {
            return null;
        }
        path = rootPath + uri;
        return path;
    }

    /**
     * 发送列表
     *
     * @param ctx
     * @param dir
     */
    private static void sendListing(ChannelHandlerContext ctx, File dir) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
//        response.headers().set("CONNECT_TYPE", "text/html;charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");

        String dirPath = dir.getPath();
        StringBuilder buf = new StringBuilder();

        buf.append("<!DOCTYPE html>\r\n");
        buf.append("<html><head><title>");
        buf.append("目录:");
        buf.append("</title></head><body>\r\n");

        buf.append("<h3>");
        buf.append(" 目录：");
        buf.append("</h3>\r\n");
        buf.append("<ul>");
        buf.append("<li><a href='../'>../</a></li> \r\n");
        for (File f : dir.listFiles()) {
            if (f.isHidden() || !f.canRead()) {
                continue;
            }
            String name = f.getName();
            //验证文件名称
//            if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
//                continue;
//            }
            buf.append("<li><a href=\"");
            buf.append(name);
            buf.append("\">");
            buf.append(name);
            buf.append("</a></li>\r\n");
        }
        buf.append("</ul></body></html>\r\n");

        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 跳转
     *
     * @param ctx
     * @param newUri
     */
    private static void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newUri);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 返回错误信息
     *
     * @param ctx
     * @param status
     */
    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        String content = "Failure: " + status.toString() + "\r\n";
        ByteBuf byteBuf = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, byteBuf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimetypesFileTypeMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimetypesFileTypeMap.getContentType(file.getPath()));
    }

    /**
     * 封装应答的回写
     *
     * @param ctx
     * @param message String的消息体Header中已经设置为Application/json
     */
    private void sendResponse(ChannelHandlerContext ctx, String message, boolean keepAlive) {

        ByteBuf byteBuf = Unpooled.copiedBuffer(message, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, byteBuf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.channel().writeAndFlush(response);
    }
}
