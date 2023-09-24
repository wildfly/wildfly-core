/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.security.sasl;

import static org.jboss.as.server.security.sasl.Constants.JBOSS_DOMAIN_SERVER;

import java.util.Map;
import java.util.function.Predicate;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.evidence.Evidence;

/**
 * The server factory for the JBOSS-DOMAIN-SERVER SASL mechanism.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DomainServerSaslServerFactory implements SaslServerFactory {

    private final SecurityDomain securityDomain;
    private final Predicate<Evidence> evidenceVerifier;

    public DomainServerSaslServerFactory(SecurityDomain securityDomain, Predicate<Evidence> evidenceVerifier) {
        super();
        this.securityDomain = securityDomain;
        this.evidenceVerifier = evidenceVerifier;
    }

    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props,
            CallbackHandler cbh) throws SaslException {
        // Unless we are sure JBOSS_DOMAIN_SERVER is required don't return a SaslServer
        return JBOSS_DOMAIN_SERVER.equals(mechanism) ? new DomainServerSaslServer(securityDomain, evidenceVerifier, cbh) : null;
    }

    public String[] getMechanismNames(final Map<String, ?> props) {
        return new String[] { JBOSS_DOMAIN_SERVER };
    }

}
