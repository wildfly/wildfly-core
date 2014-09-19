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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.Args;
import org.jboss.logging.Logger;

public class SSLTruststoreUtil {

    private static final Logger LOGGER = Logger.getLogger(SSLTruststoreUtil.class);
    public static final int HTTPS_PORT = 8443;
    public static CloseableHttpClient getHttpClientWithSSL(File keyStoreFile, String keyStorePassword, File trustStoreFile,
            String trustStorePassword) {
        try {
            final KeyStore truststore = loadKeyStore(trustStoreFile, trustStorePassword.toCharArray());
            final KeyStore keystore = keyStoreFile != null ? loadKeyStore(keyStoreFile, keyStorePassword.toCharArray()) : null;

            SSLContext sslcontext = SSLContexts.custom().loadKeyMaterial(keystore, keyStorePassword.toCharArray())
                    .loadTrustMaterial(truststore, null).build();

            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, null, null,
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).setSchemePortResolver(new SchemePortResolver() {
                public int resolve(HttpHost host) throws UnsupportedSchemeException {
                    Args.notNull(host, "HTTP host");
                    final int port = host.getPort();
                    if (port > 0) {
                        return port;
                    }
                    LOGGER.warn("target port is invalid, use default port:" + HTTPS_PORT);//
                    return HTTPS_PORT;
                }}).build();
            return httpclient;
        } catch (Exception e) {
            LOGGER.error("Creating HttpClient with customized SSL failed. We are returning the default one instead.", e);
            return HttpClients.createDefault();
        }
    }
    /**
     * Loads a JKS keystore with given path and password.
     *
     * @param keystoreFile path to keystore file
     * @param keystorePwd keystore password
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws IOException
     */
    private static KeyStore loadKeyStore(final File keystoreFile, final char[] keystorePwd) throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException, IOException {
        final KeyStore keystore = KeyStore.getInstance("JKS");
        InputStream is = null;
        try {
            is = new FileInputStream(keystoreFile);
            keystore.load(is, keystorePwd);
        } finally {
            IOUtils.closeQuietly(is);
        }
        return keystore;
    }
}
