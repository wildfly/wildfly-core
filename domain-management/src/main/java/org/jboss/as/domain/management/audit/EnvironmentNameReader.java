/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.audit;

/**
 * For use configuring the host name of the syslog audit log handler
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface EnvironmentNameReader {
    /**
     * Whether this is a server
     *
     *  @return {@code true} if a server, {@code false} if we are a host controller
     */
    boolean isServer();

    /**
     * Get the name of the server if it is a server as given in {@code ServerEnvironment.getServerName()}
     *
     * @return the name of the server
     */
    String getServerName();

    /**
     * Get the name of the host controller in the domain if it is a host controller or a domain mode server as given in @co{@code HostControllerEnvironment.getHostControllerName()}
     * or {@code ServerEnvironment.getHostControllerName()} respectively.
     *
     * @return the name of the server
     */
    String getHostName();

    /**
     * Get the name of the product to be used as the audit logger's app name.
     *
     * @return the product name, or {@code null} if this is not a product.
     */
    String getProductName();
}
