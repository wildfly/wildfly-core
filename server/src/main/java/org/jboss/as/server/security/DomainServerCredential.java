/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.security;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.security.Provider;
import java.util.function.Supplier;

import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

/**
 * A credential which is used by a domain server.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DomainServerCredential implements Credential {

    private final String token;

    public DomainServerCredential(final String token) {
        this.token = checkNotNullParam("token", token);
    }

    public String getToken() {
        return this.token;
    }

    public DomainServerCredential clone() {
        return this;
    }

    public int hashCode() {
        return token.hashCode();
    }

    public boolean equals(final Object obj) {
        return obj instanceof DomainServerCredential && token.equals(((DomainServerCredential) obj).token);
    }

    @Override
    public boolean canVerify(Evidence evidence) {
        return evidence instanceof DomainServerEvidence;
    }

    @Override
    public boolean verify(Supplier<Provider[]> providerSupplier, Evidence evidence) {
        if (canVerify(evidence)) {
            return token.equals(((DomainServerEvidence) evidence).getToken());
        }

        return false;
    }

}
