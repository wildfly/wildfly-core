/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.security.common;

import java.io.File;
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
        return getHttpClientWithSSL(null, null, trustStoreFile, password);
    }

    public static HttpClient getHttpClientWithSSL(File keyStoreFile, String keyStorePassword, File trustStoreFile, String trustStorePassword) {

        try {
            SSLContextBuilder sslContextBuilder = SSLContexts.custom()
                    .useProtocol("TLS")
                    .loadTrustMaterial(trustStoreFile, trustStorePassword.toCharArray());
            if (keyStoreFile != null) {
                sslContextBuilder.loadKeyMaterial(keyStoreFile, keyStorePassword.toCharArray(), keyStorePassword.toCharArray());
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
