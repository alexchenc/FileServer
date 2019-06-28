package com.clover;

import io.netty.handler.ssl.SslContext;
import org.apache.log4j.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;

public class SSLContextFactory {

    private static Logger logger = Logger.getLogger(SSLContextFactory.class);


    public static SSLContext getSSLContext() throws Exception {
        InputStream inputStream = null;

        SSLContext sslContext = SSLContext.getInstance("TLSv1");
        try {
            //密码
            char[] password = "Minshore1101".toCharArray();
            //证书
            KeyStore keyStore = KeyStore.getInstance("JKS");
            //加载keytool 生成的文件
            inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("local.jks");
            keyStore.load(inputStream, password);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, password);
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        } catch (Exception e) {
            LogUtil.error(logger, e);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return sslContext;
    }
}
