/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.client;

import static java.lang.System.getProperty;
import static java.lang.System.getSecurityManager;
import static java.security.AccessController.doPrivileged;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import java.io.Closeable;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.as.controller.client.impl.ClientConfigurationImpl;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.security.SecurityFactory;

/**
 * The configuration used to create the {@code ModelControllerClient}.
 *
 * @author Emanuel Muckenhuber
 */
public interface ModelControllerClientConfiguration extends Closeable {

    /**
     * Get the address of the remote host.
     *
     * @return the host name
     */
    String getHost();

    /**
     * Get the port of the remote host.
     *
     * @return the port number
     */
    int getPort();

    /**
     * Returns the requested protocol. If this is null the remoting protocol will be used.
     * If this is http or https then HTTP upgrade will be used.
     */
    String getProtocol();

    /**
     * Get the connection timeout when trying to connect to the server.
     *
     * @return the connection timeout
     */
    int getConnectionTimeout();

    /**
     * Get the security callback handler.
     *
     * @return the callback handler
     */
    CallbackHandler getCallbackHandler();

    /**
     * Get the sasl options.
     *
     * @return the sasl options
     */
    Map<String, String> getSaslOptions();

    /**
     * Get the SSLContext.
     *
     * @return the SSLContext.
     * @deprecated Use {@link ModelControllerClientConfiguration#getSslContextFactory()}
     */
    @Deprecated
    SSLContext getSSLContext();


    /**
     * Get the factory to access the SSLContext.
     *
     * @return the factory to access the SSLContext.
     */
    SecurityFactory<SSLContext> getSslContextFactory();

    /**
     * Get the executor service used for the controller client.
     *
     * @return the executor service
     */
    ExecutorService getExecutor();

    /**
     * Get the bind address used for the controller client.
     *
     * @return the bind address
     */
    String getClientBindAddress();

    /**
     * Specifies the URI for the authentication configuration.
     *
     * @return the location to the authentication configuration file or {@code null} to use auto
     * discovery
     * @deprecated this may be removed in a future release in favor of creating an
     *              {@link org.wildfly.security.auth.client.AuthenticationContext} and using a
     *              {@link org.jboss.as.controller.client.helpers.ContextualModelControllerClient}
     */
    @Deprecated
    default URI getAuthenticationConfigUri() {
        return null;
    }

    class Builder {

        // Global thread group for created pools. WFCORE-5 static to avoid leaking whenever createDefaultExecutor is called
        private static final ThreadGroup defaultThreadGroup = new ThreadGroup("management-client-thread");
        // Global count of created pools
        private static final AtomicInteger executorCount = new AtomicInteger();
        private static final String MAX_THREADS_PROP = "org.jboss.as.controller.client.max-threads";
        private static final int MAX_THREADS_DEFAULT = 6;

        private String hostName;
        private String clientBindAddress;
        private int port;
        private CallbackHandler handler;
        private Map<String, String> saslOptions;
        private SecurityFactory<SSLContext> sslContextFactory;
        private String protocol;
        private int connectionTimeout = 0;
        private URI authConfigUri;

        public Builder() {
        }

        /**
         * Sets the remote host name to which the client should connect.
         *
         * @param hostName the host name. Cannot be {@code null}
         * @return a builder to allow continued configuration
         */
        public Builder setHostName(String hostName) {
            this.hostName = hostName;
            return this;
        }

        /**
         * Sets the local address to which the client socket should be bound.
         *
         * @param clientBindAddress the local address, or {@code null} to choose one automatically
         * @return a builder to allow continued configuration
         */
        public Builder setClientBindAddress(String clientBindAddress) {
            this.clientBindAddress = clientBindAddress;
            return this;
        }

        /**
         * Sets the remote port to which the client should connect
         * @param port the port
         * @return a builder to allow continued configuration
         */
        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the handler for callbacks to obtain authentication information.
         *
         * @param handler the handler, or {@code null} if callbacks are not supported.
         *
         * @return a builder to allow continued configuration
         */
        public Builder setHandler(CallbackHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * Sets the SASL options for the remote connection
         * @param saslOptions the sasl options
         * @return a builder to allow continued configuration
         */
        public Builder setSaslOptions(Map<String, String> saslOptions) {
            this.saslOptions = saslOptions;
            return this;
        }

        /**
         * Sets the SSL context for the remote connection
         * @param sslContext the SSL context
         * @return a builder to allow continued configuration
         */
        public Builder setSslContext(final SSLContext sslContext) {
            this.sslContextFactory = () -> sslContext;
            return this;
        }

        /**
         * Sets the SSLContext factory to obtain the SSLContext from for the remote connection
         * @param sslContextFactory the SSLContext factory
         * @return a builder to allow continued configuration
         */
        public Builder setSslContextFactory(SecurityFactory<SSLContext> sslContextFactory) {
            this.sslContextFactory = sslContextFactory;
            return this;
        }

        /**
         * Sets the protocol to use for communicating with the remote process.
         *
         * @param protocol the protocol, or {@code null} if a default protocol for the
         *                 {@link #setPort(int) specified port} should be used
         * @return a builder to allow continued configuration
         */
        public Builder setProtocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Maximum time, in milliseconds, to wait for the connection to be established
         * @param connectionTimeout the timeout
         * @return a builder to allow continued configuration
         */
        public Builder setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        /**
         * Set the location of the authentication configuration file.
         * @param authConfigUri the location to the authentication configuration file or {@code null} to use auto
         *                      discovery
         * @return a builder to allow continued configuration
         * @deprecated this may be removed in a future release in favor of creating an
         *              {@link org.wildfly.security.auth.client.AuthenticationContext} and using a
         *              {@link org.jboss.as.controller.client.helpers.ContextualModelControllerClient}
         */
        @Deprecated
        public Builder setAuthenticationConfigUri(final URI authConfigUri) {
            this.authConfigUri = authConfigUri;
            return this;
        }

        /**
         * Builds the configuration object based on this builder's settings.
         *
         * @return the configuration
         */
        public ModelControllerClientConfiguration build() {
           return new ClientConfigurationImpl(hostName, port, handler, saslOptions, sslContextFactory,
                   createDefaultExecutor(), true, connectionTimeout, protocol, clientBindAddress, authConfigUri);
        }

        private static ExecutorService createDefaultExecutor() {
            final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
                public JBossThreadFactory run() {
                    return new JBossThreadFactory(defaultThreadGroup, Boolean.FALSE, null, "%G " + executorCount.incrementAndGet() + "-%t", null, null);
                }
            });
            final int maxThreads = getMaxThreads();
            return EnhancedQueueExecutor.DISABLE_HINT ?
                    new ThreadPoolExecutor(2, maxThreads, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>(), threadFactory) :
                    new EnhancedQueueExecutor.Builder()
                            .setCorePoolSize(2)
                            .setMaximumPoolSize(maxThreads)
                            .setKeepAliveTime(60, TimeUnit.SECONDS)
                            .setThreadFactory(threadFactory)
                            .build();
        }

        private static int getMaxThreads() {
            final String value = getMaxThreadsProperty();
            try {
                return value == null ? MAX_THREADS_DEFAULT : Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return MAX_THREADS_DEFAULT;
            }
        }

        private static String getMaxThreadsProperty() {
            return getSecurityManager() == null ? getProperty(MAX_THREADS_PROP) : doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return getProperty(MAX_THREADS_PROP);
                }
            });
        }

    }
}
