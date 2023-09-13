/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
