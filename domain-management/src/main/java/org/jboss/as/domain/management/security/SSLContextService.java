/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.jboss.as.domain.management.security;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Service to handle the creation of a single SSLContext based on the injected key and trust managers.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class SSLContextService implements Service {

    private final Consumer<SSLContext> sslContextConsumer;
    private final Supplier<AbstractKeyManagerService> keyManagersSupplier;
    private final Supplier<TrustManager[]> trustManagersSupplier;

    private volatile String protocol;
    private volatile Set<String> enabledCipherSuites;
    private volatile Set<String> enabledProtocols;
    private volatile SSLContext theSSLContext;

    SSLContextService(final Consumer<SSLContext> sslContextConsumer,
                      final Supplier<AbstractKeyManagerService> keyManagersSupplier,
                      final Supplier<TrustManager[]> trustManagersSupplier,
                      final String protocol, final Set<String> enabledCipherSuites, final Set<String> enabledProtocols) {
        this.sslContextConsumer = sslContextConsumer;
        this.keyManagersSupplier = keyManagersSupplier;
        this.trustManagersSupplier = trustManagersSupplier;
        this.protocol = protocol;
        this.enabledCipherSuites = enabledCipherSuites;
        this.enabledProtocols = enabledProtocols;
    }

    public String getProtocol() {
        return protocol;
    }



    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /*
     * Service Lifecycle Methods
     */

    @Override
    public void start(final StartContext context) throws StartException {
        AbstractKeyManagerService keyManagers = keyManagersSupplier != null ? keyManagersSupplier.get() : null;
        TrustManager[] trustManagers = trustManagersSupplier != null ? trustManagersSupplier.get() : null;

        try {
            SSLContext sslContext = SSLContext.getInstance(protocol);
            if (keyManagers != null && keyManagers.isLazy()) {
                sslContext = new LazyInitSSLContext(sslContext, keyManagersSupplier, trustManagersSupplier, enabledCipherSuites, enabledProtocols);
            } else {
                sslContext.init(keyManagers != null ? keyManagers.getKeyManagers() : null, trustManagers, null);
                sslContext = wrapSslContext(sslContext, enabledCipherSuites, enabledProtocols);
            }

            this.theSSLContext = sslContext;
            sslContextConsumer.accept(theSSLContext);
        } catch (NoSuchAlgorithmException e) {
            throw DomainManagementLogger.ROOT_LOGGER.unableToStart(e);
        } catch (KeyManagementException e) {
            throw DomainManagementLogger.ROOT_LOGGER.unableToStart(e);
        }
    }

    protected static SSLContext wrapSslContext(SSLContext sslContext, Set<String> enabledCipherSuites, Set<String> enabledProtocols) throws StartException {
        if (enabledCipherSuites.isEmpty() != true || enabledProtocols.isEmpty() != true) {
            SSLParameters parameters = sslContext.getSupportedSSLParameters();

            String[] commonCiphers;
            if (enabledCipherSuites.isEmpty()) {
                commonCiphers = new String[0];
            } else {
                commonCiphers = calculateCommon(parameters.getCipherSuites(), enabledCipherSuites);
                // Not valid to be empty now as there was an attempt to find a common set.
                if (commonCiphers.length == 0) {
                    throw DomainManagementLogger.ROOT_LOGGER.noCipherSuitesInCommon(
                            Arrays.asList(parameters.getCipherSuites()).toString(), enabledCipherSuites.toString());
                }
            }

            String[] commonProtocols;
            if (enabledProtocols.isEmpty()) {
                commonProtocols = new String[0];
            } else {
                commonProtocols = calculateCommon(parameters.getProtocols(), enabledProtocols);
                // Not valid to be empty now as there was an attempt to find a common set.
                if (commonProtocols.length == 0) {
                    throw DomainManagementLogger.ROOT_LOGGER.noProtocolsInCommon(Arrays.asList(parameters.getProtocols())
                            .toString(), enabledProtocols.toString());
                }
            }

            sslContext = new WrapperSSLContext(sslContext, commonCiphers, commonProtocols);
        }
        return sslContext;
    }

    private static String[] calculateCommon(String[] supported, Set<String> configured) {
        ArrayList<String> matched = new ArrayList<String>();
        for (String current : supported) {
            if (configured.contains(current)) {
                matched.add(current);
            }
        }

        return matched.toArray(new String[matched.size()]);
    }


    @Override
    public void stop(final StopContext context) {
        sslContextConsumer.accept(null);
        theSSLContext = null;
    }

    public static final class ServiceUtil {

        private static final String SERVICE_SUFFIX = "ssl-context";
        private static final String TRUST_ONLY_SERVICE_SUFFIX = SERVICE_SUFFIX + "-trust-only";

        public static ServiceName createServiceName(final ServiceName parentService, final boolean trustOnly) {
            return parentService.append(trustOnly ? TRUST_ONLY_SERVICE_SUFFIX : SERVICE_SUFFIX);
        }

        public static Supplier<SSLContext> requires(final ServiceBuilder<?> sb, final ServiceName parentService, final boolean trustOnly) {
            return sb.requires(createServiceName(parentService, trustOnly));
        }
    }


    static final class LazyInitSSLContext extends SSLContext {

        LazyInitSSLContext(final SSLContext toWrap, final Supplier<AbstractKeyManagerService> keyManagersSupplier, final Supplier<TrustManager[]> trustManagersSupplier, Set<String> enabledCipherSuites, Set<String> enabledProtocols) {
            super(new LazyInitSpi(toWrap, keyManagersSupplier, trustManagersSupplier, enabledCipherSuites, enabledProtocols), toWrap.getProvider(), toWrap.getProtocol());
        }

        private static class LazyInitSpi extends SSLContextSpi {

            private volatile SSLContext wrapped;
            private volatile boolean init = false;
            private volatile SSLServerSocketFactory serverSocketFactory;
            private volatile SSLSocketFactory socketFactory;

            final Supplier<AbstractKeyManagerService> keyManagersSupplier;
            final Supplier<TrustManager[]> trustManagersSupplier;
            private final Set<String> enabledCipherSuites;
            private final Set<String> enabledProtocols;

            private void doInit() {
                if(!init) {
                    synchronized (this) {
                        if(!init) {
                            try {
                                AbstractKeyManagerService keyManagers = keyManagersSupplier != null ? keyManagersSupplier.get() : null;
                                TrustManager[] trustManagers = trustManagersSupplier != null ? trustManagersSupplier.get() : null;
                                wrapped.init(keyManagers.getKeyManagers(), trustManagers, null);
                                wrapped = wrapSslContext(wrapped, enabledCipherSuites, enabledProtocols);
                            } catch (Exception e) {
                                throw DomainManagementLogger.SECURITY_LOGGER.failedToCreateLazyInitSSLContext(e);
                            } finally {
                                init = true;
                            }
                        }
                    }
                }

            }

            private LazyInitSpi(final SSLContext wrapped, final Supplier<AbstractKeyManagerService> keyManagersSupplier,
                                final Supplier<TrustManager[]> trustManagersSupplier, Set<String> enabledCipherSuites, Set<String> enabledProtocols) {
                this.wrapped = wrapped;
                this.keyManagersSupplier = keyManagersSupplier;
                this.trustManagersSupplier = trustManagersSupplier;
                this.enabledCipherSuites = enabledCipherSuites;
                this.enabledProtocols = enabledProtocols;
            }

            @Override
            protected SSLEngine engineCreateSSLEngine() {
                doInit();
                return wrapped.createSSLEngine();
            }

            @Override
            protected SSLEngine engineCreateSSLEngine(String host, int port) {
                doInit();
                return wrapped.createSSLEngine(host, port);
            }

            @Override
            protected SSLSessionContext engineGetClientSessionContext() {
                return wrapped.getClientSessionContext();
            }

            @Override
            protected SSLSessionContext engineGetServerSessionContext() {
                return wrapped.getServerSessionContext();
            }

            @Override
            protected SSLServerSocketFactory engineGetServerSocketFactory() {
                if(serverSocketFactory == null) {
                    synchronized (this) {
                        if(serverSocketFactory == null) {
                            serverSocketFactory = new LazySSLServerSocketFactory(this);
                        }
                    }
                }
                return serverSocketFactory;
            }

            @Override
            protected SSLSocketFactory engineGetSocketFactory() {
                if(socketFactory == null) {
                    synchronized (this) {
                        if(socketFactory == null) {
                            socketFactory = new LazySSLSocketFactory(this);
                        }
                    }
                }
                return socketFactory;
            }

            @Override
            protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) throws KeyManagementException {
                wrapped.init(km, tm, sr);
            }

            private class LazySSLSocketFactory extends SSLSocketFactory {

                private final LazyInitSpi wrapped;
                private volatile SSLSocketFactory factory;

                private LazySSLSocketFactory(final LazyInitSpi wrapped) {
                    this.wrapped = wrapped;
                }

                @Override
                public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                    createFactory();
                    return factory.createSocket(s, host, port, autoClose);
                }

                protected void createFactory() {
                    if(factory == null) {
                        synchronized (this) {
                            if(factory == null) {
                                wrapped.doInit();
                                factory = wrapped.wrapped.getSocketFactory();
                            }
                        }
                    }
                }

                @Override
                public String[] getDefaultCipherSuites() {
                    if(factory == null) {
                        return wrapped.wrapped.getDefaultSSLParameters().getCipherSuites();
                    }
                    return factory.getDefaultCipherSuites();
                }

                @Override
                public String[] getSupportedCipherSuites() {
                    if(factory == null) {
                        return wrapped.wrapped.getSupportedSSLParameters().getCipherSuites();
                    }
                    return factory.getSupportedCipherSuites();
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    createFactory();
                    return factory.createSocket(host,port);
                }

                @Override
                public Socket createSocket(InetAddress host, int port) throws IOException {
                    createFactory();
                    return factory.createSocket(host, port);
                }

                @Override
                public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                    createFactory();
                    return factory.createSocket(host, port, localHost, localPort);
                }

                @Override
                public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                        throws IOException {
                    createFactory();
                    return factory.createSocket(address, port, localAddress, localPort);
                }

                public Socket createSocket(Socket s, InputStream consumed, boolean autoClose) throws IOException {
                    createFactory();
                    return factory.createSocket(s, consumed, autoClose);
                }

                public Socket createSocket() throws IOException {
                    createFactory();
                    return factory.createSocket();
                }
            }

            private class LazySSLServerSocketFactory extends SSLServerSocketFactory {

                private final LazyInitSpi wrapped;
                private volatile SSLServerSocketFactory factory;

                private LazySSLServerSocketFactory(final LazyInitSpi wrapped) {
                    this.wrapped = wrapped;
                }
                protected void createFactory() {
                    if(factory == null) {
                        synchronized (this) {
                            if(factory == null) {
                                wrapped.doInit();
                                factory = wrapped.wrapped.getServerSocketFactory();
                            }
                        }
                    }
                }

                @Override
                public String[] getDefaultCipherSuites() {
                    if(factory == null) {
                        return wrapped.wrapped.getDefaultSSLParameters().getCipherSuites();
                    }
                    return factory.getDefaultCipherSuites();
                }

                @Override
                public String[] getSupportedCipherSuites() {
                    if(factory == null) {
                        return wrapped.wrapped.getSupportedSSLParameters().getCipherSuites();
                    }
                    return factory.getSupportedCipherSuites();
                }

                @Override
                public ServerSocket createServerSocket(int port) throws IOException {
                    doInit();
                    return factory.createServerSocket(port);
                }

                @Override
                public ServerSocket createServerSocket(int port, int backlog) throws IOException {
                    doInit();
                    return factory.createServerSocket(port, backlog);
                }

                @Override
                public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
                    doInit();
                    return factory.createServerSocket(port, backlog, ifAddress);
                }
            }
        }

    }
}
