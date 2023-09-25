/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * A {@link PasswordValidation} to verify that a password is not in a list of banned passwords.
 *
 * @author baranowb
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ValueRestriction implements PasswordRestriction {

    private final Set<String> forbiddenValues;

    private final String requirementsMessage;

    private final boolean must;

    public ValueRestriction(final String[] forbiddenValues, final boolean must) {
        this.forbiddenValues = new HashSet<String>(Arrays.asList(forbiddenValues));
        this.must = must;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < forbiddenValues.length; i++) {
            sb.append(forbiddenValues[i]);
            if (i + 1 < forbiddenValues.length) {
                sb.append(", ");
            }
        }
        requirementsMessage = must ? DomainManagementLogger.ROOT_LOGGER.passwordMustNotEqualInfo(sb.toString()) : DomainManagementLogger.ROOT_LOGGER.passwordShouldNotEqualInfo(sb.toString());
    }

    @Override
    public String getRequirementMessage() {
        return requirementsMessage;
    }

    @Override
    public void validate(String userName, String password) throws PasswordValidationException {
        if (forbiddenValues.contains(password)) {
            throw must ? DomainManagementLogger.ROOT_LOGGER.passwordMustNotBeEqual(password) : DomainManagementLogger.ROOT_LOGGER.passwordShouldNotBeEqual(password);
        }
    }

}
