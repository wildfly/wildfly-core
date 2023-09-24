/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

import java.util.List;

/**
 * Declaration of checker contract.
 *
 * @author baranowb
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface PasswordStrengthChecker {

    /**
     * Determines password strength. Checks and algorithm are implementation specific.
     * @param userName - The username the password is being specified for.
     * @param password - password which is going to be inspected
     * @param restictions - adhoc password restriction list. Those will be additionally checked against password.
     * @return result indicating strength of password and possible failure reasons.
     */
    PasswordStrengthCheckResult check(String userName, String password, List<PasswordRestriction> restictions);

}
