/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client.helpers;

import java.util.function.BiFunction;

/**
 * Provides utilities related to working with names of JBoss Modules modules.
 */
public final class JBossModulesNameUtil {

    /**
     * Provides the canonical string representation of a module identifier from a string
     * of the form {@code name[:slot]}. The canonical representation will not include
     * slot information if the slot is {@code main}.
     *
     * @param moduleSpec a module name specification in the form {@code name[:slot]}. Cannot be {@code null}
     * @return the canonical representation. Will not return @{code null}
     */
    public static String parseCanonicalModuleIdentifier(String moduleSpec) {
        return parseModuleIdentifier(moduleSpec, JBossModulesNameUtil::canonicalModuleIdentifier);
    }

    /**
     * Parses the given module identifier into name and optional slot elements, passing those to the given
     * function and returning the result of that function.
     * <p/>
     * This variant does not {@link #parseCanonicalModuleIdentifier(String) canonicalize} the given identifier.
     *
     * @param moduleIdentifier an  identifier for a module. Cannot be {@code null}
     * @param function a function to apply to the module's name and optional slot. Cannot be {@code null}.
     *                 The slot value passed to the function may be null if the identifier does not contain one.
     * @return the value returned by {@code function}
     * @param <R> the type returned by {@code function}
     */
    public static <R> R parseModuleIdentifier(String moduleIdentifier, BiFunction<String, String, R> function) {
        return parseModuleIdentifier(moduleIdentifier, function, false, null);
    }


    /**
     * Parses the given module identifier into name and optional slot elements, passing those to the given
     * function and returning the result of that function.
     * <p/>
     *
     * @param moduleIdentifier an identifier for a module. Cannot be {@code null}
     * @param function a function to apply to the module's name and optional slot. Cannot be {@code null}.
     *                 The slot value passed to the function may be null if the identifier does not contain one.
     * @param canonicalize if {@code true} the identifier will be {@link #parseCanonicalModuleIdentifier(String) canonicalized} before parsing
     * @return the value returned by {@code function}
     * @param <R> the type returned by {@code function}
     */
    public static <R> R parseModuleIdentifier(String moduleIdentifier, BiFunction<String, String, R> function, boolean canonicalize) {
        return parseModuleIdentifier(moduleIdentifier, function, canonicalize, null);
    }


    /**
     * Parses the given module identifier into name and optional slot elements, passing those to the given
     * function and returning the result of that function.
     * <p/>
     *
     * @param moduleIdentifier an identifier for a module. Cannot be {@code null}
     * @param function a function to apply to the module's name and optional slot. Cannot be {@code null}.
     *                 The slot value passed to the function may be null if the identifier does not contain one.
     * @param canonicalize if {@code true} the identifier will be {@link #parseCanonicalModuleIdentifier(String) canonicalized} before parsing
     * @param defaultSlot  string to pass to {@code function} as the slot parameter if the identifier doesn't include a slot value. May be {@code null}
     * @return the value returned by {@code function}
     * @param <R> the type returned by {@code function}
     */
    public static <R> R parseModuleIdentifier(String moduleIdentifier, BiFunction<String, String, R> function,
                                              boolean canonicalize, String defaultSlot) {
        if (canonicalize) {
            moduleIdentifier = parseCanonicalModuleIdentifier(moduleIdentifier);
        }

        // Note: this is taken from org.jboss.modules.ModuleIdentifier.fromString and lightly adapted.

        if (moduleIdentifier == null) {
            throw new IllegalArgumentException("Module specification is null");
        } else if (moduleIdentifier.isEmpty()) {
            throw new IllegalArgumentException("Empty module specification");
        } else {
            StringBuilder b = new StringBuilder();

            int c;
            int i;
            for(i = 0; i < moduleIdentifier.length(); i = moduleIdentifier.offsetByCodePoints(i, 1)) {
                c = moduleIdentifier.codePointAt(i);
                if (c == 92) {
                    b.appendCodePoint(c);
                    i = moduleIdentifier.offsetByCodePoints(i, 1);
                    if (i >= moduleIdentifier.length()) {
                        throw new IllegalArgumentException("Name has an unterminated escape");
                    }

                    c = moduleIdentifier.codePointAt(i);
                    b.appendCodePoint(c);
                } else {
                    if (c == 58) {
                        i = moduleIdentifier.offsetByCodePoints(i, 1);
                        if (i == moduleIdentifier.length()) {
                            throw new IllegalArgumentException("Slot is empty");
                        }
                        break;
                    }

                    b.appendCodePoint(c);
                }
            }

            String name = b.toString();
            b.setLength(0);
            if (i >= moduleIdentifier.length()) {
                return function.apply(name, defaultSlot);
            } else {
                do {
                    c = moduleIdentifier.codePointAt(i);
                    b.appendCodePoint(c);
                    i = moduleIdentifier.offsetByCodePoints(i, 1);
                } while(i < moduleIdentifier.length());

                return function.apply(name, b.toString());
            }
        }

    }

    /**
     * Provides the canonical string representation of a module identifier from a base
     * module name and an optional slot. The canonical representation will not include
     * slot information if the slot is {@code main}.
     *
     * @param name the base module name. Cannot be {@code null}
     * @param slot the module slot. May be @{code null}
     *
     * @return the canonical representation. Will not return @{code null}
     */
    public static String canonicalModuleIdentifier(String name, String slot) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        String escaped = escapeName(name);
        return slot == null || "main".equals(slot) ? escaped : escaped + ":" + escapeSlot(slot);
    }

    private static String escapeName(String name) {
        // Note: this is taken from org.jboss.modules.ModuleIdentifier.escapeName
        StringBuilder b = new StringBuilder();
        boolean escaped = false;
        int i = 0;

        while(i < name.length()) {
            int c = name.codePointAt(i);
            switch (c) {
                case 58:
                case 92:
                    escaped = true;
                    b.append('\\');
                default:
                    b.appendCodePoint(c);
                    i = name.offsetByCodePoints(i, 1);
            }
        }

        return escaped ? b.toString() : name;
    }

    private static String escapeSlot(String slot) {
        // Note: this is taken from org.jboss.modules.ModuleIdentifier.escapeSlot
        StringBuilder b = new StringBuilder();
        boolean escaped = false;
        int i = 0;

        while(i < slot.length()) {
            int c = slot.codePointAt(i);
            switch (c) {
                case 92:
                    escaped = true;
                    b.append('\\');
                default:
                    b.appendCodePoint(c);
                    i = slot.offsetByCodePoints(i, 1);
            }
        }

        return escaped ? b.toString() : slot;
    }
}