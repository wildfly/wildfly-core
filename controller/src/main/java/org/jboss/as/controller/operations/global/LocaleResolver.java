/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

/**
 * Utility class handling low-level parsing of Locale string tag.
 *
 * @author Romain Pelisse - <romain@redhat.com>
 */
public final class LocaleResolver {

    private static final String ENGLISH = new Locale("en").getLanguage();

    private LocaleResolver(){};

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
