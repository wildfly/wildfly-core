/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.security.sasl;

import static org.jboss.as.server.security.sasl.Constants.JBOSS_DOMAIN_SERVER;

import java.util.Collections;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;

import org.wildfly.common.Assert;

/**
 * The client factory for the JBOSS-DOMAIN-SERVER SASL mechanism.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class DomainServerSaslClientFactory implements SaslClientFactory {

    public static final SaslClientFactory INSTANCE = new DomainServerSaslClientFactory();

    public SaslClient createSaslClient(final String[] mechanisms, final String authorizationId, final String protocol, final String serverName, Map<String, ?> props, final CallbackHandler cbh) throws SaslException {
        Assert.checkNotNullParam("cbh", cbh);
        if (props == null) props = Collections.emptyMap();
        for (String mechanism : mechanisms) {
            if (JBOSS_DOMAIN_SERVER.equals(mechanism)) {
                return new DomainServerSaslClient(cbh);
            }
        }
        return null;
    }

    public String[] getMechanismNames(final Map<String, ?> props) {
        return new String[] { JBOSS_DOMAIN_SERVER };
    }

    /**
     * Get the singleton instance of the {@code SaslClientFactory}
     *
     * @return the singleton instance of the {@code SaslClientFactory}
     */
    public static SaslClientFactory getInstance() {
        return INSTANCE;
    }

}
