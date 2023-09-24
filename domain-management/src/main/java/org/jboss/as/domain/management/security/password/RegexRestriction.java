/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

import java.util.regex.Pattern;

/**
 *
 * @author baranowb
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RegexRestriction implements PasswordRestriction {

    private final String regex;
    private final String requirementsMessage;
    private final String failureMessage;

    /**
     * @param regex
     * @param message
     */
    public RegexRestriction(String regex, String requirementsMessage, String failureMessage) {
        this.regex = regex;
        this.requirementsMessage = requirementsMessage;
        this.failureMessage = failureMessage;
    }

    @Override
    public String getRequirementMessage() {
        return requirementsMessage;
    }

    @Override
    public void validate(String userName, String password) throws PasswordValidationException {
        if (Pattern.matches(this.regex, password) == false) {
            throw new PasswordValidationException(failureMessage);
        }
    }
}
