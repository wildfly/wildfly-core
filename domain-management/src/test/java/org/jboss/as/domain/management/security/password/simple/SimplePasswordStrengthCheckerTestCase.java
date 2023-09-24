/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password.simple;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.domain.management.security.password.Dictionary;
import org.jboss.as.domain.management.security.password.Keyboard;
import org.jboss.as.domain.management.security.password.LengthRestriction;
import org.jboss.as.domain.management.security.password.PasswordCheckUtil;
import org.jboss.as.domain.management.security.password.PasswordRestriction;
import org.jboss.as.domain.management.security.password.PasswordStrengthCheckResult;
import org.jboss.as.domain.management.security.password.RestrictionLevel;
import org.jboss.as.domain.management.security.password.ValueRestriction;

/**
 * @author baranowb
 */
public class SimplePasswordStrengthCheckerTestCase {

    private Keyboard keyboard = new SimpleKeyboard();
    private Dictionary dictionary = new SimpleDictionary();

    public static final PasswordCheckUtil PCU = PasswordCheckUtil.create(RestrictionLevel.REJECT);
    public static final PasswordRestriction ALPHA_RESTRICTION = PCU.createAlphaRestriction(1);
    public static final PasswordRestriction SYMBOL_RESTRICTION = PCU.createSymbolRestriction(1);
    public static final PasswordRestriction DIGIT_RESTRICTION = PCU.createDigitRestriction(1);
    public static final LengthRestriction LENGTH_RESTRICTION = new LengthRestriction(8, true);

    @Test
    public void testLengthRestriction() {
        List<PasswordRestriction> restrictions = new ArrayList<PasswordRestriction>();
        //one that will fail
        restrictions.add(LENGTH_RESTRICTION);
        //one that will pass
        restrictions.add(SYMBOL_RESTRICTION);
        SimplePasswordStrengthChecker checker = new SimplePasswordStrengthChecker(restrictions, this.dictionary, this.keyboard);
        String pwd = "1W2sa#4";
        PasswordStrengthCheckResult result = checker.check("", pwd, null);
        assertNotNull(result);
        assertNotNull(result.getPassedRestrictions());
        assertNotNull(result.getRestrictionFailures());

        assertEquals(1, result.getPassedRestrictions().size());
        assertEquals(1, result.getRestrictionFailures().size());

        assertNotNull(result.getStrength());

        assertEquals(DomainManagementLogger.ROOT_LOGGER.passwordNotLongEnough(8).getMessage(), result.getRestrictionFailures().get(0).getMessage());
        assertEquals(SYMBOL_RESTRICTION, result.getPassedRestrictions().get(0));
    }

    @Test
    public void testDigitsRestriction() {
        List<PasswordRestriction> restrictions = new ArrayList<PasswordRestriction>();
        //one that will fail
        restrictions.add(DIGIT_RESTRICTION);
        //one that will pass
        restrictions.add(ALPHA_RESTRICTION);
        SimplePasswordStrengthChecker checker = new SimplePasswordStrengthChecker(restrictions, this.dictionary, this.keyboard);
        String pwd = "DW$sa#x";
        PasswordStrengthCheckResult result = checker.check("", pwd, null);
        assertNotNull(result);
        assertNotNull(result.getPassedRestrictions());
        assertNotNull(result.getRestrictionFailures());

        assertEquals(1, result.getPassedRestrictions().size());
        assertEquals(1, result.getRestrictionFailures().size());

        assertNotNull(result.getStrength());

        assertEquals(DomainManagementLogger.ROOT_LOGGER.passwordMustHaveDigit(1), result.getRestrictionFailures().get(0).getMessage());
        assertEquals(ALPHA_RESTRICTION, result.getPassedRestrictions().get(0));
    }

    @Test
    public void testSymbolsRestriction() {
        List<PasswordRestriction> restrictions = new ArrayList<PasswordRestriction>();
        //one that will fail
        restrictions.add(SYMBOL_RESTRICTION);
        //one that will pass
        restrictions.add(ALPHA_RESTRICTION);
        SimplePasswordStrengthChecker checker = new SimplePasswordStrengthChecker(restrictions, this.dictionary, this.keyboard);
        String pwd = "DW5sa3x";
        PasswordStrengthCheckResult result = checker.check("", pwd, null);
        assertNotNull(result);
        assertNotNull(result.getPassedRestrictions());
        assertNotNull(result.getRestrictionFailures());

        assertEquals(1, result.getPassedRestrictions().size());
        assertEquals(1, result.getRestrictionFailures().size());

        assertNotNull(result.getStrength());

        assertEquals(DomainManagementLogger.ROOT_LOGGER.passwordMustHaveSymbol(1), result.getRestrictionFailures().get(0).getMessage());
        assertEquals(ALPHA_RESTRICTION, result.getPassedRestrictions().get(0));
    }

    @Test
    public void testAlphaRestriction() {
        List<PasswordRestriction> restrictions = new ArrayList<PasswordRestriction>();
        //one that will fail
        restrictions.add(ALPHA_RESTRICTION);
        //one that will pass
        restrictions.add(SYMBOL_RESTRICTION);
        SimplePasswordStrengthChecker checker = new SimplePasswordStrengthChecker(restrictions, this.dictionary, this.keyboard);
        String pwd = "!#*_33";
        PasswordStrengthCheckResult result = checker.check("", pwd, null);
        assertNotNull(result);
        assertNotNull(result.getPassedRestrictions());
        assertNotNull(result.getRestrictionFailures());

        assertEquals(1, result.getPassedRestrictions().size());
        assertEquals(1, result.getRestrictionFailures().size());

        assertNotNull(result.getStrength());

        assertEquals(DomainManagementLogger.ROOT_LOGGER.passwordMustHaveAlpha(1), result.getRestrictionFailures().get(0).getMessage());
        assertEquals(SYMBOL_RESTRICTION, result.getPassedRestrictions().get(0));
    }

    @Test
    public void testAdHocRestriction() {
        List<PasswordRestriction> restrictions = new ArrayList<PasswordRestriction>();
        restrictions.add(ALPHA_RESTRICTION);
        restrictions.add(SYMBOL_RESTRICTION);
        SimplePasswordStrengthChecker checker = new SimplePasswordStrengthChecker(restrictions, this.dictionary, this.keyboard);
        String pwd = "!#*_3x";
        List<PasswordRestriction> adHocRestrictions = new ArrayList<PasswordRestriction>();
        ValueRestriction restriction = new ValueRestriction(new String[] { pwd }, true);
        adHocRestrictions.add(restriction);

        PasswordStrengthCheckResult result = checker.check("", pwd, adHocRestrictions);
        assertNotNull(result);
        assertNotNull(result.getPassedRestrictions());
        assertNotNull(result.getRestrictionFailures());

        assertEquals(2, result.getPassedRestrictions().size());
        assertEquals(1, result.getRestrictionFailures().size());

        assertNotNull(result.getStrength());

        assertEquals(ALPHA_RESTRICTION, result.getPassedRestrictions().get(0));
        assertEquals(SYMBOL_RESTRICTION, result.getPassedRestrictions().get(1));
        assertEquals(DomainManagementLogger.ROOT_LOGGER.passwordMustNotBeEqual(pwd).getMessage(), result.getRestrictionFailures().get(0).getMessage());
    }
}
