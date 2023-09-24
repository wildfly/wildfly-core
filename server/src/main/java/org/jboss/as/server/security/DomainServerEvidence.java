/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.security;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.security.Principal;

import org.wildfly.security.evidence.Evidence;

/**
 * A piece of evidence representing the authenticating domain server.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class DomainServerEvidence implements Evidence {

    private final Principal serverPrincipal;
    private final String token;

    public DomainServerEvidence(Principal serverPrincipal, String token) {
        this.serverPrincipal = checkNotNullParam("serverPrincipal", serverPrincipal);
        this.token = checkNotNullParam("token", token);
    }

    @Override
    public Principal getDefaultPrincipal() {
        return serverPrincipal;
    }

    /**
     * Get the server's security token.
     *
     * @return the server's security token
     */
    public String getToken() {
        return this.token;
    }

}
