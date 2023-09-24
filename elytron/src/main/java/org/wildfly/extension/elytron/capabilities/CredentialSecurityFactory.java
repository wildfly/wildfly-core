/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron.capabilities;

import org.wildfly.security.SecurityFactory;
import org.wildfly.security.credential.Credential;

/**
 * A {@link SecurityFactory} that returns a {@link Credential}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface CredentialSecurityFactory extends SecurityFactory<Credential> {

    static CredentialSecurityFactory from(final SecurityFactory<? extends Credential> function) {
        return function::create;
    }

}
