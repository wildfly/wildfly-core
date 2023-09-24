/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.management;

import java.util.List;
import java.util.Map;

import org.xnio.OptionMap;

/**
 * Policy information for the HTTP management interface that is common across both standalone and domain mode.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface HttpInterfaceCommonPolicy {

    /**
     * Get the name of the HTTP authentication factory to use to secure the interface for normal HTTP requests.
     *
     * @return The name of the SASL authentication factory to use to secure the interface for normal HTTP requests.
     */
    String getHttpAuthenticationFactory();

    /**
     * Get the name of the SSLContext to use to enable SSL for this management interface.
     *
     * @return the name of the SSLContext to use to enable SSL for this management interface.
     */
    String getSSLContext();

    /**
     * Get the name of the SASL authentication factory to use to secure the interface where HTTP upgrade is used.
     *
     * @return The name of the SASL authentication factory to use to secure the interface where HTTP upgrade is used.
     */
    String getSaslAuthenticationFactory();

    /**
     * Is the management console enabled, is set to {@code false} the console should not be made available.
     *
     * @return {@code true} if the management console should be made available, {@code false} otherwise.
     */
    boolean isConsoleEnabled();

    /**
     * Is upgrading to a Remoting connection over the HTTP interface enabled.
     *
     * @return {@code true} if HTTP Upgrade to the native protocol is enabled, {@code false} otherwise.
     */
    boolean isHttpUpgradeEnabled();

    /**
     * Get the connector options based on the current configuration.
     *
     * @return the connector options based on the current configuration.
     */
    OptionMap getConnectorOptions();

    /**
     * Get the list of origins that the server should accept requests from, if none set then all forms of cross origin resource sharing are disabled.
     *
     * An empty {@link List} or {@code null} both signal that cross origin resource sharing should be disabled.
     *
     * @return The list of origins that the server should accept requests from.
     */
    List<String> getAllowedOrigins();

    /**
     * A set of HTTP headers that should be set on each response based on matching the key of the map as being a prefix of the requested path.
     *
     * A prefix is inclusive of an exact match.
     *
     * @return A {@link Map} of the constant headers to be set on each response with the key being used as the prefix.
     */
    Map<String, List<Header>> getConstantHeaders();

    static class Header {
        final String name;
        final String value;

        Header(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

    }

}

