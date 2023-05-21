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
import org.wildfly.security.permission.PermissionVerifier;

/**
 * The server factory for the JBOSS-DOMAIN-SERVER SASL mechanism.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DomainServerSaslServerFactory implements SaslServerFactory {

    private final SecurityDomain securityDomain;
    private final Predicate<Evidence> evidenceVerifier;
    private final PermissionVerifier permissionVerifier;

    public DomainServerSaslServerFactory(SecurityDomain securityDomain, Predicate<Evidence> evidenceVerifier, PermissionVerifier permissionVerifier) {
        super();
        this.securityDomain = securityDomain;
        this.evidenceVerifier = evidenceVerifier;
        this.permissionVerifier = permissionVerifier;
    }

    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props,
            CallbackHandler cbh) throws SaslException {
        // Unless we are sure JBOSS_DOMAIN_SERVER is required don't return a SaslServer
        return JBOSS_DOMAIN_SERVER.equals(mechanism) ? new DomainServerSaslServer(securityDomain, evidenceVerifier, cbh, permissionVerifier) : null;
    }

    public String[] getMechanismNames(final Map<String, ?> props) {
        return new String[] { JBOSS_DOMAIN_SERVER };
    }

}
