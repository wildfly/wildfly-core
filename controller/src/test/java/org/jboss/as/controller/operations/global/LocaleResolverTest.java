/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
