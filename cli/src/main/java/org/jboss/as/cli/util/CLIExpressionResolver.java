/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.util;

import java.io.File;

import org.jboss.as.cli.parsing.UnresolvedExpressionException;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author Alexey Loubyansky
 */
public class CLIExpressionResolver {

    /** File separator value */
    private static final String FILE_SEPARATOR = File.separator;

    /** Path separator value */
    private static final String PATH_SEPARATOR = File.pathSeparator;

    /** File separator alias */
    private static final String FILE_SEPARATOR_ALIAS = "/";

    /** Path separator alias */
    private static final String PATH_SEPARATOR_ALIAS = ":";

    /** Environment variable prefix */
    private static final String ENV_PREFIX = "env.";

    private static final int INITIAL = 0;
    private static final int DOLLAR = 1;

    /**
     * Attempts to substitute all the found expressions in the input
     * with their corresponding resolved values.
     * If any of the found expressions failed to resolve the exception will be thrown.
     * If the input does not contain any expression, the input is returned as is.
     *
     * @param input  the input string
     * @return  the input with resolved expressions
     * @throws UnresolvedExpressionException  in case an expression could not be resolved
     */
    public static String resolve(String input) throws UnresolvedExpressionException {
        return resolve(input, true);
    }

    /**
     * Attempts to substitute all the found expressions in the input
     * with their corresponding resolved values.
     * If any of the found expressions failed to resolve or
     * if the input does not contain any expression, the input is returned as is.
     *
     * @param input  the input string
     * @return  the input with resolved expressions or the original input in case
     *          the input didn't contain any expressions or at least one of the
     *          expressions could not be resolved
     */
    public static String resolveOrOriginal(String input) {
        try {
            return resolve(input, true);
        } catch (UnresolvedExpressionException e) {
            return input;
        }
    }

    /**
     * Attempts to substitute all the found expressions in the input with their corresponding resolved values. If any of the
     * found expressions failed to resolve, try to resolve expressions as more as possible and return the resolved value. If the
     * input does not contain any expression, the input is returned as is. see https://issues.jboss.org/browse/WFCORE-1980,
     *
     * @param input the input string
     * @return the input with resolved expressions or the original input in case the input didn't contain any expressions
     */
    public static String resolveLax(String input) {
        try {
            return resolve(input, false);
        } catch (UnresolvedExpressionException e) {
            // XXX OK. It should not reach here as exceptionIfNotResolved is false.
            return input;
        }
    }

    private static String resolve(String input, boolean exceptionIfNotResolved)
            throws UnresolvedExpressionException {

        int state = INITIAL;
        int i = 0;
        while(i < input.length()) {
            final char c = input.charAt(i++);
            if(c == '$') {
                if(state == DOLLAR) {
                    state = INITIAL;
                    final StringBuilder buf = new StringBuilder(input.length() - 1);
                    buf.append(input.substring(0, --i));
                    if(i + 1 < input.length()) {
                        buf.append(input.substring(i + 1));
                    }
                    input = buf.toString();
                } else {
                    state = DOLLAR;
                }
            } else if(c == '{') {
                if(state == DOLLAR) {
                    state = INITIAL;
                    final String[] inputRef = new String[]{input};
                    if(i - 2 == resolveProperty2(inputRef, i - 2, exceptionIfNotResolved)) {
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
    public static String resolveProperty(String input, final int location, boolean exceptionIfNotResolved)
            throws UnresolvedExpressionException {
        final String[] inputRef = new String[]{input};
        if(location == resolveProperty2(inputRef, location, exceptionIfNotResolved)) {
            return inputRef[0];
        }
        return input;
    }

    private static int resolveProperty2(String[] inputRef, final int location, boolean exceptionIfNotResolved)
            throws UnresolvedExpressionException {

        // there must be $ at location and { after it
        int nestingLevel = 1;
        int state = INITIAL;
        final StringBuilder expression = new StringBuilder();
        int i = location + 2;
        while (i < inputRef[0].length()) {
            final char c = inputRef[0].charAt(i++);
            switch (c) {
                case '$':
                    if (state == DOLLAR) {
                        expression.append(c);
                        state = INITIAL;
                    } else {
                        state = DOLLAR;
                    }
                    break;
                case '{':
                    if (state == DOLLAR) {
                        state = INITIAL;
                        if(expression.length() > 0 && expression.charAt(expression.length() - 1) == ':') {
                            expression.append("${");
                            ++nestingLevel;
                        } else {
                            i = resolveProperty2(inputRef, i - 2, exceptionIfNotResolved);
                        }
                    } else {
                        expression.append(c);
                    }
                    break;
                case '}':
                    if (state == DOLLAR) {
                        expression.append('$');
                    }

                    if(--nestingLevel > 0) {
                        state = INITIAL;
                        expression.append(c);
                        continue;
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
                        expression.append('$');
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
