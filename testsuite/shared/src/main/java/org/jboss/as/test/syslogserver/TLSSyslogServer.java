/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.syslogserver;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.productivity.java.syslog4j.SyslogRuntimeException;
import org.productivity.java.syslog4j.server.impl.net.tcp.ssl.SSLTCPNetSyslogServerConfigIF;

/**
 * TCP syslog server implementation for syslog4j.
 *
 * @author Josef Cacek
 */
public class TLSSyslogServer extends TCPSyslogServer {

    private static final Logger LOGGER = Logger.getLogger(TLSSyslogServer.class);

    private SSLContext sslContext;

    /**
     * Creates custom sslContext from keystore and truststore configured in
     *
     * @see org.productivity.java.syslog4j.server.impl.net.tcp.TCPNetSyslogServer#initialize()
     */
    @Override
    public void initialize() throws SyslogRuntimeException {
        super.initialize();

        final SSLTCPNetSyslogServerConfigIF config = (SSLTCPNetSyslogServerConfigIF) this.tcpNetSyslogServerConfig;

        try {
            final char[] keystorePwd = config.getKeyStorePassword().toCharArray();
            final KeyStore keystore = loadKeyStore(config.getKeyStore(), keystorePwd);
            final char[] truststorePassword = config.getTrustStorePassword().toCharArray();
            final KeyStore truststore = loadKeyStore(config.getTrustStore(), truststorePassword);

            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, keystorePwd);

            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory
                    .getDefaultAlgorithm());
            trustManagerFactory.init(truststore);

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        } catch (Exception e) {
            LOGGER.error("Exception occurred during SSLContext for TLS syslog server initialization", e);
            throw new SyslogRuntimeException(e);
        }
    }

    /**
     * Returns {@link javax.net.ServerSocketFactory} from custom {@link javax.net.ssl.SSLContext} instance created in {@link #initialize()} method.
     *
     * @see org.productivity.java.syslog4j.server.impl.net.tcp.TCPNetSyslogServer#getServerSocketFactory()
     */
    @Override
    protected ServerSocketFactory getServerSocketFactory() throws IOException {
        return sslContext.getServerSocketFactory();
    }

    /**
     * Loads a JKS keystore with given path and password.
     *
     * @param keystoreFile path to keystore file
     * @param keystorePwd keystore password
     * @return the keystore
     * @throws java.security.KeyStoreException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.security.cert.CertificateException
     * @throws java.io.IOException
     */
    private static KeyStore loadKeyStore(final String keystoreFile, final char[] keystorePwd) throws KeyStoreException,
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
