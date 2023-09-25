/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.impl;

import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

/**
 * Transformation class of the model controller client configuration to the
 * underlying protocol configs.
 *
 * @author Emanuel Muckenhuber
 */
class ProtocolConfigurationFactory {

    private static final OptionMap DEFAULT_OPTIONS = OptionMap.EMPTY;

    static ProtocolConnectionConfiguration create(final ModelControllerClientConfiguration client, final Endpoint endpoint) throws URISyntaxException {

        URI connURI;
        if(client.getProtocol() == null) {
            // WFLY-1462 for compatibility assume remoting if the standard native port is configured
            String protocol = client.getPort() == 9999 ? "remote://" : "remote+http://";
            connURI = new URI(protocol + formatPossibleIpv6Address(client.getHost()) +  ":" + client.getPort());
        } else  {
            connURI = new URI(client.getProtocol() + "://" + formatPossibleIpv6Address(client.getHost()) +  ":" + client.getPort());
        }
        final ProtocolConnectionConfiguration configuration = ProtocolConnectionConfiguration.create(endpoint, connURI, DEFAULT_OPTIONS);

        configuration.setClientBindAddress(client.getClientBindAddress());
        final long timeout = client.getConnectionTimeout();
        if(timeout > 0) {
            configuration.setConnectionTimeout(timeout);
        }
        return configuration;
    }

    private static String formatPossibleIpv6Address(String address) {
        if (address == null) {
            return address;
        }
        if (!address.contains(":")) {
            return address;
        }
        if (address.startsWith("[") && address.endsWith("]")) {
            return address;
        }
        return "[" + address + "]";
    }

}
