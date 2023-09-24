/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.global;

import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * @author Romain Pelisse <belaran@gredhat.com>
 */
public class LocaleResolverTest {

    Locale french = Locale.FRENCH;
    Locale english = Locale.ENGLISH;
    Locale german = Locale.GERMAN;

    @Test
    public void invalidLanguageNotUsingAlphanumericCharacters() {
        invalidLanguageOrCountry("1#");
    }

    @Test
    public void invalidCountryNotUsingAlphanumericCharacters() {
        invalidLanguageOrCountry("en_1€");
    }

    @Test
    public void invalidLanguageTag() {
        validLanguageOrCountry("en-US-x-lvariant-POSIX");
        invalidLanguageOrCountry("e"); // too short
        invalidLanguageOrCountry("e_U");
        invalidLanguageOrCountry("en_U");
        invalidLanguageOrCountry("en_US_"); // incomplete
    }

    @Test
    public void nonExistingLanguageOrCountry() {
        invalidLanguageOrCountry("aW_AW");
    }

    @Test
    public void invalidLocaleSeparator() {
        invalidLanguageOrCountry("*");
        invalidLanguageOrCountry("de_DE8");
        invalidLanguageOrCountry("en_#");
    }

    public void invalidLanguageOrCountry(String unparsed) {
        try {
            LocaleResolver.resolveLocale(unparsed);
        } catch ( IllegalArgumentException e) {
            assertEquals(unparsed,e.getMessage());
            return; // pass...
        }
        fail("Format " + unparsed + " is invalid, test should have failed.");
    }

    public void validLanguageOrCountry(String unparsed) {
        try {
            LocaleResolver.resolveLocale(unparsed);
        } catch ( IllegalArgumentException e) {
            assertEquals(unparsed,e.getMessage());
            fail("Format " + unparsed + " is valid, test should not have failed.");
        }
    }

    @Test
    public void wfly3723() {
        assertEquals("",french, LocaleResolver.resolveLocale(french.toLanguageTag()));
        assertEquals("",german, LocaleResolver.resolveLocale(german.toLanguageTag()));
        assertEquals("",Locale.ROOT, LocaleResolver.resolveLocale(english.toLanguageTag()));
    }
}
