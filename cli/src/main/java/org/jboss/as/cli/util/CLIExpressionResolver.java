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

package org.jboss.as.cli.util;

import java.io.File;

import org.jboss.as.cli.Util;
import org.jboss.as.cli.parsing.UnresolvedExpressionException;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author Alexey Loubyansky
 */
public class CLIExpressionResolver {

    /** File separator value */
    private static final String FILE_SEPARATOR;
    static {
        // due to '\' being an escape character and recursive property replacement,
        // on windows the path separator has to be escaped
        FILE_SEPARATOR = Util.isWindows() ? File.separator + File.separator : File.separator;
    }

    /** Path separator value */
    private static final String PATH_SEPARATOR = File.pathSeparator;

    /** File separator alias */
    private static final String FILE_SEPARATOR_ALIAS = "/";

    /** Path separator alias */
    private static final String PATH_SEPARATOR_ALIAS = ":";

    /** Environment variable prefix */
    private static final String ENV_PREFIX = "env.";

    private static final int INITIAL = 0;
    private static final int ESCAPE = 1;
    private static final int DOLLAR = 2;

    public static String resolve(String input) throws UnresolvedExpressionException {
        return resolve(input, true);
    }

    public static String resolveAvailable(String input) {
        try {
            return resolve(input, false);
        } catch (UnresolvedExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resolve(String input, boolean exceptionIfNotResolved)
            throws UnresolvedExpressionException {

        int state = INITIAL;
        int i = 0;
        while(i < input.length()) {
            final char c = input.charAt(i++);
            if(state == ESCAPE) {
                state = INITIAL;
                continue;
            }
            if(c == '\\') {
                state = ESCAPE;
            } else if(c == '$') {
                state = DOLLAR;
            } else if(c == '{') {
                if(state == DOLLAR) {
                    state = INITIAL;
                    final String[] inputRef = new String[]{input};
                    if(i - 2 == resolveProperty(inputRef, i - 2, exceptionIfNotResolved)) {
                        input = inputRef[0];
                        i -= 2;
                    }
                }
            }
        }
        return input;
    }

    /**
     *
     * @param location the index of '$' starting the system property
     * @param exceptionIfNotResolved whether to ignore unresolved expression or throw an exception
     * @return the index the parsing should continue from
     * @throws UnresolvedExpressionException in case the expression could not be resolved
     */
    public static int resolveProperty(String[] inputRef, final int location, boolean exceptionIfNotResolved)
            throws UnresolvedExpressionException {

        // there must be $ at location and { after it
        int state = INITIAL;
        final StringBuilder expression = new StringBuilder();
        int i = location + 2;
        while (i < inputRef[0].length()) {
            final char c = inputRef[0].charAt(i++);
            if (state == ESCAPE) {
                expression.append(c);
                state = INITIAL;
                continue;
            }
            switch (c) {
                case '\\':
                    state = ESCAPE;
                    break;
                case '$':
                    if (state == DOLLAR) {
                        expression.append(c);
                    } else {
                        state = DOLLAR;
                    }
                    break;
                case '{':
                    if (state == DOLLAR) {
                        state = INITIAL;
                        i = resolveProperty(inputRef, i - 2, exceptionIfNotResolved);
                    } else {
                        expression.append(c);
                    }
                    break;
                case '}':
                    if (state == DOLLAR) {
                        expression.append('$');
                    }

                    final String propName = expression.toString();
                    final String resolved = resolveKey(propName);
                    if (resolved != null) {
                        final String input = inputRef[0];
                        final StringBuilder buf = new StringBuilder(input.length() - i + location + resolved.length());
                        buf.append(input.substring(0, location)).append(resolved);
                        if (i < input.length()) {
                            buf.append(input.substring(i));
                        }
                        inputRef[0] = buf.toString();
                        return location;
                    } else if (exceptionIfNotResolved) {
                        throw new UnresolvedExpressionException(inputRef[0].substring(location, i), "Unrecognized system property "
                                + propName);
                    }

                    return i;
                default:
                    if (state == DOLLAR) {
                        state = INITIAL;
                    }
                    expression.append(c);
            }
        }
        return i;
    }

    private static String resolveKey(String key) {
        String value = null;
        // check for alias
        if (FILE_SEPARATOR_ALIAS.equals(key)) {
            value = FILE_SEPARATOR;
        } else if (PATH_SEPARATOR_ALIAS.equals(key)) {
            value = PATH_SEPARATOR;
        } else {
            // check from the properties
            value = WildFlySecurityManager.getPropertyPrivileged(key, null);

            if(value == null && key.startsWith(ENV_PREFIX)) {
                value = System.getenv(key.substring(4));
            }

            if (value == null) {
                // Check for a default value ${key:default}
                int colon = key.indexOf(':');
                if (colon > 0) {
                    String realKey = key.substring(0, colon);
                    value = WildFlySecurityManager.getPropertyPrivileged(realKey, null);

                    if(value == null && realKey.startsWith(ENV_PREFIX)) {
                        value = System.getenv(realKey.substring(4));
                    }

                    if (value == null) {
                        // Check for a composite key, "key1,key2"
                        value = resolveCompositeKey(realKey);

                        // Not a composite key either, use the
                        // specified default
                        if (value == null)
                            value = key.substring(colon + 1);
                    }
                } else {
                    // No default, check for a composite key,
                    // "key1,key2"
                    value = resolveCompositeKey(key);
                }
            }
        }
        return value;
    }
    /**
     * Try to resolve a "key" from the provided properties by checking if it is actually a "key1,key2", in which case try first
     * "key1", then "key2". If all fails, return null.
     *
     * It also accepts "key1," and ",key2".
     *
     * @param key the key to resolve
     * @param props the properties to use
     * @return the resolved key or null
     */
    private static String resolveCompositeKey(String key) {
        String value = null;

        // Look for the comma
        int comma = key.indexOf(',');
        if (comma > -1) {
            // If we have a first part, try resolve it
            if (comma > 0) {
                // Check the first part
                String key1 = key.substring(0, comma);
                value = WildFlySecurityManager.getPropertyPrivileged(key1, null);
                if (value == null && key1.startsWith(ENV_PREFIX)) {
                    value = System.getenv(key1.substring(4));
                }

            }
            // Check the second part, if there is one and first lookup failed
            if (value == null && comma < key.length() - 1) {
                String key2 = key.substring(comma + 1);
                value = WildFlySecurityManager.getPropertyPrivileged(key2, null);
                if (value == null && key2.startsWith(ENV_PREFIX)) {
                    value = System.getenv(key2.substring(4));
                }
            }
        }
        // Return whatever we've found or null
        return value;
    }
}
