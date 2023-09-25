/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

/**
 * Interface to be implemented by a password restriction.
 *
 * @author baranowb
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface PasswordRestriction {

    String getRequirementMessage();

    void validate(final String userName, final String password) throws PasswordValidationException;

}
