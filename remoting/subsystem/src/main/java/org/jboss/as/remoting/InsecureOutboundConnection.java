/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import java.net.URI;

import javax.net.ssl.SSLContext;

import org.jboss.as.network.OutboundConnection;
import org.wildfly.security.auth.client.AuthenticationConfiguration;

/**
 * {@link OutboundConnection} with no authentication/encryption.
 * @author Paul Ferraro
 */
public class InsecureOutboundConnection implements OutboundConnection {

    private final URI uri;

    InsecureOutboundConnection(URI uri) {
        this.uri = uri;
    }

    @Override
    public URI getDestinationUri() {
        return this.uri;
    }

    @Override
    public AuthenticationConfiguration getAuthenticationConfiguration() {
        return AuthenticationConfiguration.empty();
    }

    @Override
    public SSLContext getSSLContext() {
        return null;
    }
}
