/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.password;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * A {@link PasswordRestriction} to verify that the username and password and not equal.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class UsernamePasswordMatch implements PasswordRestriction {

    private final boolean must;

    public UsernamePasswordMatch(final boolean must) {
        this.must = must;
    }

    @Override
    public String getRequirementMessage() {
        if (must) {
            return DomainManagementLogger.ROOT_LOGGER.passwordUsernameMustMatchInfo();
        }
        return DomainManagementLogger.ROOT_LOGGER.passwordUsernameShouldMatchInfo();
    }

    @Override
    public void validate(String userName, String password) throws PasswordValidationException {
        if (userName.equals(password)) {
            throw must ? DomainManagementLogger.ROOT_LOGGER.passwordUsernameMatchError() : DomainManagementLogger.ROOT_LOGGER.passwordUsernameShouldNotMatch();
        }
    }

}
