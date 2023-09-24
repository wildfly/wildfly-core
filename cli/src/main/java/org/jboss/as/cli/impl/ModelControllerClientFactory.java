/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.protocol.ProtocolTimeoutHandler;
import org.wildfly.security.SecurityFactory;

/**
 * @author Alexey Loubyansky
 *
 */
public interface ModelControllerClientFactory {

    String SASL_DISALLOWED_MECHANISMS = "SASL_DISALLOWED_MECHANISMS";
    String JBOSS_LOCAL_USER = "JBOSS-LOCAL-USER";

    Map<String, String> DISABLED_LOCAL_AUTH = Collections.singletonMap(SASL_DISALLOWED_MECHANISMS, JBOSS_LOCAL_USER);
    Map<String, String> ENABLED_LOCAL_AUTH = Collections.emptyMap();

    interface ConnectionCloseHandler {
        void handleClose();
    }

    ModelControllerClient getClient(ControllerAddress address, CallbackHandler handler,
            boolean disableLocalAuth, SecurityFactory<SSLContext> sslContextFactory, boolean fallbackSslContext, int connectionTimeout,
            ConnectionCloseHandler closeHandler, ProtocolTimeoutHandler timeoutHandler, String clientBindAddress) throws IOException;

    ModelControllerClientFactory DEFAULT = new ModelControllerClientFactory() {
        @Override
        public ModelControllerClient getClient(ControllerAddress address, CallbackHandler handler,
                boolean disableLocalAuth, SecurityFactory<SSLContext> sslContextFactory, boolean fallbackSslContext, int connectionTimeout,
                ConnectionCloseHandler closeHandler, ProtocolTimeoutHandler timeoutHandler, String clientBindAddress) throws IOException {
            // TODO - Make use of the ProtocolTimeoutHandler
            Map<String, String> saslOptions = disableLocalAuth ? DISABLED_LOCAL_AUTH : ENABLED_LOCAL_AUTH;
            ModelControllerClientConfiguration config = new ModelControllerClientConfiguration.Builder()
                    .setProtocol(address.getProtocol())
                    .setHostName(address.getHost())
                    .setPort(address.getPort())
                    .setHandler(handler)
                    .setSslContextFactory(sslContextFactory)
                    .setConnectionTimeout(connectionTimeout)
                    .setSaslOptions(saslOptions)
                    .setClientBindAddress(clientBindAddress)
                    .build();
            return ModelControllerClient.Factory.create(config);
        }
    };

    ModelControllerClientFactory CUSTOM = new ModelControllerClientFactory() {

        @Override
        public ModelControllerClient getClient(ControllerAddress address,
                final CallbackHandler handler, boolean disableLocalAuth, final SecurityFactory<SSLContext> sslContextFactory, final boolean fallbackSslContext,
                final int connectionTimeout, final ConnectionCloseHandler closeHandler, ProtocolTimeoutHandler timeoutHandler, String clientBindAddress) throws IOException {
            Map<String, String> saslOptions = disableLocalAuth ? DISABLED_LOCAL_AUTH : ENABLED_LOCAL_AUTH;
            return new CLIModelControllerClient(address, handler, connectionTimeout, closeHandler, saslOptions, sslContextFactory, fallbackSslContext, timeoutHandler, clientBindAddress);
        }};

}
