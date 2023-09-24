/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.password;

/**
 * An {@link Exception} thrown by the password validators if password validation fails.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PasswordValidationException extends Exception {

    private static final long serialVersionUID = -7334606274496438504L;

    public PasswordValidationException(final String message) {
        super(message);
    }

}
