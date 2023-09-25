/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * A {@link PasswordRestriction} to check the length of the password.
 *
 * @author baranowb
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class LengthRestriction implements PasswordRestriction {

    private final int desiredLength;
    private final boolean must;

    /**
     * @param desiredLength
     */
    public LengthRestriction(int desiredLength, boolean must) {
        this.desiredLength = desiredLength;
        this.must = must;
    }

    @Override
    public String getRequirementMessage() {
        return DomainManagementLogger.ROOT_LOGGER.passwordLengthInfo(desiredLength);
    }

    @Override
    public void validate(String userName, String password) throws PasswordValidationException {
        if (password == null || password.length() < this.desiredLength) {
            throw must ? DomainManagementLogger.ROOT_LOGGER.passwordNotLongEnough(desiredLength)
                    : DomainManagementLogger.ROOT_LOGGER.passwordShouldHaveXCharacters(desiredLength);
        }
    }
}
