/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
