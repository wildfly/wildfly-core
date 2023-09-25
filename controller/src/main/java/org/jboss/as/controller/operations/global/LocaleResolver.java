/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.global;

import java.util.Locale;

/**
 * Utility class handling low-level parsing of Locale string tag.
 *
 * @author Romain Pelisse - <romain@redhat.com>
 */
public final class LocaleResolver {

    private static final String ENGLISH = new Locale("en").getLanguage();

    private LocaleResolver(){}

    static Locale resolveLocale(String unparsed) throws IllegalArgumentException {
        Locale locale = forLanguageTag(unparsed);

        if ( "".equals(locale.getLanguage()) ) {
            throw new IllegalArgumentException(unparsed);
        }

        return replaceByRootLocaleIfLanguageIsEnglish(locale);
    }

    private static Locale forLanguageTag(String unparsed) {
        try {
            return Locale.forLanguageTag(unparsed);
        } catch ( StringIndexOutOfBoundsException  e ) {
            throw new IllegalArgumentException(unparsed);
        }
    }

    /**
     * <p>By substituting Locale.ROOT for any locale that includes English, we're counting on the fact that the locale is in the end
     * used in a call to {@link java.util.ResourceBundle#getBundle(java.lang.String, java.util.Locale, java.lang.ClassLoader) }.</p>
     *
     * <p>Note that Locale.ROOT bypasses the default locale (which could be French or German). It relies on the convention we used for
     *    naming bundles files in Wildfly (LocalDescriptions.properties, not LocalDescriptions_en.properties).</p>
     */
    static Locale replaceByRootLocaleIfLanguageIsEnglish(Locale locale) {
        return (locale.getLanguage().equals(ENGLISH) ? Locale.ROOT : locale);
    }
}
