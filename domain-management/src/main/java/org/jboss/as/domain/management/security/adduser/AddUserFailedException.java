/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

/**
 * A {@link RuntimeException} thrown in non-interactive mode to indicate failure.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AddUserFailedException extends RuntimeException {

    private static final long serialVersionUID = -6526393394847040107L;

    AddUserFailedException(final String message) {
        super(message);
    }

}
