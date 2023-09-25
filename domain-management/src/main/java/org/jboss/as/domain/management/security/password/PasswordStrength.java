/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

/**
 * Enum with values indicating strength of password.
 * @author baranowb
 *
 */
public enum PasswordStrength {

    VERY_WEAK(0), WEAK(1), MODERATE(2), MEDIUM(3), STRONG(4), VERY_STRONG(5), EXCEPTIONAL(6);

    private int strength;

    PasswordStrength(int s) {
        this.strength = s;
    }

    public int getStrength() {
        return this.strength;
    }
}
