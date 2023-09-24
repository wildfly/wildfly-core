/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.management;

import org.xnio.OptionMap;

/**
 * Policy information for the native mangement interface that is common across both standalone and domain mode.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface NativeInterfaceCommonPolicy {

    /**
     * Get the name of the SASL authentication factory to use to secure the native interface.
     *
     * @return The name of the SASL authentication factory to use to secure the native interface.
     */
    String getSaslAuthenticationFactory();

    /**
     * Get the name of the SSLContext to use to enable SSL for this management interface.
     *
     * @return the name of the SSLContext to use to enable SSL for this management interface.
     */
    String getSSLContext();

    /**
     * Get the connector options based on the current configuration.
     *
     * @return the connector options based on the current configuration.
     */
    OptionMap getConnectorOptions();

}
