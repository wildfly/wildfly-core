/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Provides utilities related to working with names of JBoss Modules modules.
 */
public final class ModuleIdentifierUtil {

    /**
     * Provides the canonical string representation of a module identifier from a string
     * of the form {@code name[:slot]}. The canonical representation will not include
     * slot information if the slot is {@code main}.
     *
     * @param moduleSpec a module name specification in the form {@code name[:slot]}. Cannot be {@code null}
     * @return the canonical representation. Will not return @{code null}
     */
    public static String canonicalModuleIdentifier(String moduleSpec) {
        // Note: this is taken from org.jboss.modules.ModuleIdentifier.fromString and lightly adapted.

        if (moduleSpec == null) {
            throw new IllegalArgumentException("Module specification is null");
        } else if (moduleSpec.isEmpty()) {
            throw new IllegalArgumentException("Empty module specification");
        } else {
            StringBuilder b = new StringBuilder();

            int c;
            int i;
            for(i = 0; i < moduleSpec.length(); i = moduleSpec.offsetByCodePoints(i, 1)) {
                c = moduleSpec.codePointAt(i);
                if (c == 92) {
                    b.appendCodePoint(c);
                    i = moduleSpec.offsetByCodePoints(i, 1);
                    if (i >= moduleSpec.length()) {
                        throw new IllegalArgumentException("Name has an unterminated escape");
                    }

                    c = moduleSpec.codePointAt(i);
                    b.appendCodePoint(c);
                } else {
                    if (c == 58) {
                        i = moduleSpec.offsetByCodePoints(i, 1);
                        if (i == moduleSpec.length()) {
                            throw new IllegalArgumentException("Slot is empty");
                        }
                        break;
                    }

                    b.appendCodePoint(c);
                }
            }

            String name = b.toString();
            b.setLength(0);
            if (i >= moduleSpec.length()) {
                return canonicalModuleIdentifier(name, null);
            } else {
                do {
                    c = moduleSpec.codePointAt(i);
                    b.appendCodePoint(c);
                    i = moduleSpec.offsetByCodePoints(i, 1);
                } while(i < moduleSpec.length());

                return canonicalModuleIdentifier(name, b.toString());
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

    /**
     * A {@link ParameterCorrector} that {@link #canonicalModuleIdentifier(String) canonicalizes}
     * values that are meant to represent JBoss Modules module names.
     */
    public static final ParameterCorrector MODULE_NAME_CORRECTOR = new ParameterCorrector() {
        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            if (ModelType.STRING.equals(newValue.getType())) {
                String orig = newValue.asString();
                String corrected = canonicalModuleIdentifier(orig);
                if (!orig.equals(corrected)) {
                    newValue.set(corrected);
                }
            }
            return newValue;
        }
    };

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
