/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.impl;

import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.wildfly.security.SecurityFactory;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ExecutorService;


/**
 * @author Emanuel Muckenhuber
 */
public class ClientConfigurationImpl implements ModelControllerClientConfiguration {

    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
    private final String address;
    private final String clientBindAddress;
    private final int port;
    private final CallbackHandler handler;
    private final Map<String, String> saslOptions;
    private final SecurityFactory<SSLContext> sslContextFactory;
    private final ExecutorService executorService;
    private final String protocol;
    private final boolean shutdownExecutor;
    private final int connectionTimeout;
    private final URI authConfigUri;

    public ClientConfigurationImpl(String address, int port, CallbackHandler handler, Map<String, String> saslOptions, SecurityFactory<SSLContext> sslContextFactory, ExecutorService executorService, boolean shutdownExecutor, final int connectionTimeout, final String protocol, String clientBindAddress, final URI authConfigUri) {
        this.address = address;
        this.port = port;
        this.handler = handler;
        this.saslOptions = saslOptions;
        this.sslContextFactory = sslContextFactory;
        this.executorService = executorService;
        this.shutdownExecutor = shutdownExecutor;
        this.protocol = protocol;
        this.clientBindAddress = clientBindAddress;
        this.connectionTimeout = connectionTimeout > 0 ? connectionTimeout : DEFAULT_CONNECTION_TIMEOUT;
        this.authConfigUri = authConfigUri;
    }

    @Override
    public String getHost() {
        return address;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }


    @Override
    public CallbackHandler getCallbackHandler() {
        return handler;
    }

    @Override
    public Map<String, String> getSaslOptions() {
        return saslOptions;
    }

    @Override
    public SSLContext getSSLContext() {
        try {
            return sslContextFactory != null ? sslContextFactory.create() : null;
        } catch (GeneralSecurityException e) {
            ControllerClientLogger.ROOT_LOGGER.trace("Unable to create SSLContext", e);
            return null;
        }
    }

    @Override
    public SecurityFactory<SSLContext> getSslContextFactory() {
        return sslContextFactory;
    }

    @Override
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @Override
    public ExecutorService getExecutor() {
        return executorService;
    }

    @Override
    public void close() {
        if(shutdownExecutor && executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public String getClientBindAddress() {
        return clientBindAddress;
    }

    @Override
    public URI getAuthenticationConfigUri() {
        return authConfigUri;
    }
}
