/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.jboss.logging.Logger;

public class SSLTruststoreUtil {

    private static final Logger LOGGER = Logger.getLogger(SSLTruststoreUtil.class);

    public static final int HTTPS_PORT = 8443;
    public static final int HTTPS_PORT_VERIFY_FALSE = 18443;
    public static final int HTTPS_PORT_VERIFY_WANT = 18444;
    public static final int HTTPS_PORT_VERIFY_TRUE = 18445;

    public static final int[] HTTPS_PORTS = {HTTPS_PORT_VERIFY_FALSE, HTTPS_PORT_VERIFY_TRUE, HTTPS_PORT_VERIFY_WANT};

    private static final String HTTPS = "https";

    public static HttpClient getHttpClientWithSSL(File trustStoreFile, String password) {
        return getHttpClientWithSSL(null, null, null, trustStoreFile, password, "JKS");
    }

    public static HttpClient getHttpClientWithSSL(File trustStoreFile, String password, String provider) {
        return getHttpClientWithSSL(null, null, null, trustStoreFile, password, provider);
    }

    public static HttpClient getHttpClientWithSSL(File keyStoreFile, String keyStorePassword, File trustStoreFile, String trustStorePassword) {
        return getHttpClientWithSSL(keyStoreFile, keyStorePassword, "JKS", trustStoreFile, trustStorePassword, "JKS");
    }

    public static HttpClient getHttpClientWithSSL(File keyStoreFile, String keyStorePassword, String keyStoreProvider,
            File trustStoreFile, String trustStorePassword, String trustStoreProvider) {

        try {
            KeyStore trustStore = KeyStore.getInstance(trustStoreProvider);
            try (FileInputStream fis = new FileInputStream(trustStoreFile)) {
                trustStore.load(fis, trustStorePassword.toCharArray());
            }
            SSLContextBuilder sslContextBuilder = SSLContexts.custom()
                    .setProtocol("TLS")
                    .loadTrustMaterial(trustStore, null);
            if (keyStoreFile != null) {
                KeyStore keyStore = KeyStore.getInstance(keyStoreProvider);
                try (FileInputStream fis = new FileInputStream(keyStoreFile)) {
                    keyStore.load(fis, keyStorePassword.toCharArray());
                }
                sslContextBuilder.loadKeyMaterial(keyStore, keyStorePassword.toCharArray(), null);
            }
            SSLContext sslContext = sslContextBuilder.build();
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", socketFactory)
                    .build();

            return HttpClientBuilder.create()
                    .setSSLSocketFactory(socketFactory)
                            //.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setConnectionManager(new PoolingHttpClientConnectionManager(registry))
                    .setSchemePortResolver(new DefaultSchemePortResolver())
                    .build();

        } catch (Exception e) {
            LOGGER.error("Creating HttpClient with customized SSL failed. We are returning the default one instead.", e);
            return HttpClients.createDefault();
        }
    }


}
