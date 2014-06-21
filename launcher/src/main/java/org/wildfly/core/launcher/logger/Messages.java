/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.wildfly.core.launcher.logger;

import static java.security.AccessController.doPrivileged;

import java.lang.reflect.Field;
import java.security.PrivilegedAction;
import java.util.Locale;

/**
 * Copied the logging Messages to use 0 dependencies for this module.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Messages {

    private Messages() {
    }

    /**
     * Get a message bundle of the given type.
     *
     * @param type the bundle type class
     *
     * @return the bundle
     */
    public static <T> T getBundle(final Class<T> type) {
        return doPrivileged(new PrivilegedAction<T>() {
            public T run() {
                final Locale locale = Locale.getDefault();
                final String lang = locale.getLanguage();
                final String country = locale.getCountry();
                final String variant = locale.getVariant();

                Class<? extends T> bundleClass = null;
                if (variant != null && !variant.isEmpty()) try {
                    bundleClass = Class.forName(join(type.getName(), "$bundle", lang, country, variant), true, type.getClassLoader()).asSubclass(type);
                } catch (ClassNotFoundException e) {
                    // ignore
                }
                if (bundleClass == null && country != null && !country.isEmpty()) try {
                    bundleClass = Class.forName(join(type.getName(), "$bundle", lang, country, null), true, type.getClassLoader()).asSubclass(type);
                } catch (ClassNotFoundException e) {
                    // ignore
                }
                if (bundleClass == null && lang != null && !lang.isEmpty()) try {
                    bundleClass = Class.forName(join(type.getName(), "$bundle", lang, null, null), true, type.getClassLoader()).asSubclass(type);
                } catch (ClassNotFoundException e) {
                    // ignore
                }
                if (bundleClass == null) try {
                    bundleClass = Class.forName(join(type.getName(), "$bundle", null, null, null), true, type.getClassLoader()).asSubclass(type);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Invalid bundle " + type + " (implementation not found)");
                }
                final Field field;
                try {
                    field = bundleClass.getField("INSTANCE");
                } catch (NoSuchFieldException e) {
                    throw new IllegalArgumentException("Bundle implementation " + bundleClass + " has no instance field");
                }
                try {
                    return type.cast(field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException("Bundle implementation " + bundleClass + " could not be instantiated", e);
                }
            }
        });
    }

    private static String join(final String interfaceName, final String type, final String lang, final String country, final String variant) {
        final StringBuilder build = new StringBuilder();
        build.append(interfaceName).append('_').append(type);
        if (lang != null && !lang.isEmpty()) {
            build.append('_');
            build.append(lang);
        }
        if (country != null && !country.isEmpty()) {
            build.append('_');
            build.append(country);
        }
        if (variant != null && !variant.isEmpty()) {
            build.append('_');
            build.append(variant);
        }
        return build.toString();
    }
}
