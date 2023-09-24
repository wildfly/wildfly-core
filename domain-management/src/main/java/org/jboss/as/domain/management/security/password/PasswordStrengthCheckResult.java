/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

import java.util.List;

/**
 * Represents strength check result.
 *
 * @author baranowb
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface PasswordStrengthCheckResult {
    /**
     * List of restrictions that password did not met.
     * @return
     */
    List<PasswordValidationException> getRestrictionFailures();
    /**
     * Password strength.
     * @return
     */
    PasswordStrength getStrength();
    /**
     * List of restrictions that password did met.
     * @return
     */
    List<PasswordRestriction> getPassedRestrictions();
}
