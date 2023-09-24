/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

import org.junit.Test;

/**
 *
 * @author baranowb
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PasswordRestrictionsTestCase {

    @Test(expected = PasswordValidationException.class)
    public void testLengthRestrictionFail() throws PasswordValidationException {
        LengthRestriction lr = new LengthRestriction(2, true);
        lr.validate("", "1");
    }

    @Test
    public void testLengthRestrictionPass() throws PasswordValidationException {
        LengthRestriction lr = new LengthRestriction(2, true);
        lr.validate("", "12");
    }

    @Test(expected = PasswordValidationException.class)
    public void testValueRestrictionFail() throws PasswordValidationException {
        ValueRestriction lr = new ValueRestriction(new String[] { "restricted" }, true);
        lr.validate("", "restricted");
    }

    @Test
    public void testValueRestrictionPass() throws PasswordValidationException {
        ValueRestriction lr = new ValueRestriction(new String[] { "restricted" }, true);
        lr.validate("", "12");
    }

    @Test(expected = PasswordValidationException.class)
    public void testRegexRestrictionFail() throws PasswordValidationException {
        RegexRestriction lr = new RegexRestriction("\\d*", "", "");
        lr.validate("", "xxxAAA");
    }

    @Test
    public void testRegexRestrictionPass() throws PasswordValidationException {
        RegexRestriction lr = new RegexRestriction("x*ax+", "", "");
        lr.validate("", "xxax");
    }

    @Test(expected = PasswordValidationException.class)
    public void testUsernameMatchFail() throws PasswordValidationException {
        UsernamePasswordMatch upm = new UsernamePasswordMatch(true);
        upm.validate("darranl", "darranl");
    }

    @Test
    public void testUsernameMatchPass() throws PasswordValidationException {
        UsernamePasswordMatch upm = new UsernamePasswordMatch(true);
        upm.validate("darranl", "password");
    }

}
