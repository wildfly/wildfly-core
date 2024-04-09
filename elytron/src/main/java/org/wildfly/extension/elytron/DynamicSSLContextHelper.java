/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.dynamic.ssl.DynamicSSLContext;
import org.wildfly.security.dynamic.ssl.DynamicSSLContextImpl;
import org.wildfly.security.dynamic.ssl.DynamicSSLContextException;

import javax.net.ssl.SSLContext;
import java.security.GeneralSecurityException;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

/**
 * Helper class for obtaining an instance of DynamicSSLContext created from the provided AuthenticationContext
 */
class DynamicSSLContextHelper {

    /**
     * Get DynamicSSLContext instance from the provided authentication context
     * @param authenticationContext authentication context to use with the DynamicSSLContext
     * @return DynamicSSLContext instance
     */
    static SSLContext getDynamicSSLContextInstance(AuthenticationContext authenticationContext) {
        try {
            return new DynamicSSLContext(new DynamicSSLContextImpl(authenticationContext));
        } catch (DynamicSSLContextException |  GeneralSecurityException e) {
            throw ROOT_LOGGER.unableToObtainDynamicSSLContext();
        }
    }
}
