/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.network;

import java.net.URI;

import javax.net.ssl.SSLContext;

import org.wildfly.security.auth.client.AuthenticationConfiguration;

/**
 * Allows callers to get information of an outbound connection.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public interface OutboundConnection {

    /**
     * Get the destination URI for the connection.
     *
     * @return the destination URI
     */
    URI getDestinationUri();

    /**
     * Get the connection authentication configuration.  This is derived either from the authentication information
     * defined on the resource, or the linked authentication configuration named on the resource.
     *
     * @return the authentication configuration
     */
    AuthenticationConfiguration getAuthenticationConfiguration();

    /**
     * Get the connection SSL Context.
     *
     * @return the SSL context
     */
    SSLContext getSSLContext();

}
