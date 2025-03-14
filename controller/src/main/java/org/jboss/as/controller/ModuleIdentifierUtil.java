/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.function.BiFunction;

import org.jboss.as.controller.client.helpers.JBossModulesNameUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Provides utilities related to working with names of JBoss Modules modules.
 *
 * @deprecated use {@link JBossModulesNameUtil} instead
 */
@Deprecated(forRemoval = true, since = "28.0.0")
public final class ModuleIdentifierUtil {

    /**
     * Provides the canonical string representation of a module identifier from a string
     * of the form {@code name[:slot]}. The canonical representation will not include
     * slot information if the slot is {@code main}.
     *
     * @param moduleSpec a module name specification in the form {@code name[:slot]}. Cannot be {@code null}
     * @return the canonical representation. Will not return @{code null}
     *
     * @deprecated use {@link JBossModulesNameUtil#parseCanonicalModuleIdentifier(String)}
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public static String canonicalModuleIdentifier(String moduleSpec) {
        return JBossModulesNameUtil.parseCanonicalModuleIdentifier(moduleSpec);
    }

    /**
     * Parses the given module identifier into name and optional slot elements, passing those to the given
     * function and returning the result of that function.
     * <p/>
     * This variant does not {@link #canonicalModuleIdentifier(String) canonicalize} the given identifier.
     *
     * @param moduleIdentifier an  identifier for a module. Cannot be {@code null}
     * @param function a function to apply to the module's name and optional slot. Cannot be {@code null}.
     *                 The slot value passed to the function may be null if the identifier does not contain one.
     * @return the value returned by {@code function}
     * @param <R> the type returned by {@code function}
     *
     * @deprecated use {@link JBossModulesNameUtil#parseModuleIdentifier(String, BiFunction)}
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public static <R> R parseModuleIdentifier(String moduleIdentifier, BiFunction<String, String, R> function) {
        return JBossModulesNameUtil.parseModuleIdentifier(moduleIdentifier, function);
    }


    /**
     * Parses the given module identifier into name and optional slot elements, passing those to the given
     * function and returning the result of that function.
     * <p/>
     *
     * @param moduleIdentifier an identifier for a module. Cannot be {@code null}
     * @param function a function to apply to the module's name and optional slot. Cannot be {@code null}.
     *                 The slot value passed to the function may be null if the identifier does not contain one.
     * @param canonicalize if {@code true} the identifier will be {@link #canonicalModuleIdentifier(String) canonicalized} before parsing
     * @return the value returned by {@code function}
     * @param <R> the type returned by {@code function}
     *
     * @deprecated use {@link JBossModulesNameUtil#parseModuleIdentifier(String, BiFunction, boolean)}
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public static <R> R parseModuleIdentifier(String moduleIdentifier, BiFunction<String, String, R> function, boolean canonicalize) {
        return JBossModulesNameUtil.parseModuleIdentifier(moduleIdentifier, function, canonicalize);
    }


    /**
     * Parses the given module identifier into name and optional slot elements, passing those to the given
     * function and returning the result of that function.
     * <p/>
     *
     * @param moduleIdentifier an identifier for a module. Cannot be {@code null}
     * @param function a function to apply to the module's name and optional slot. Cannot be {@code null}.
     *                 The slot value passed to the function may be null if the identifier does not contain one.
     * @param canonicalize if {@code true} the identifier will be {@link #canonicalModuleIdentifier(String) canonicalized} before parsing
     * @param defaultSlot  string to pass to {@code function} as the slot parameter if the identifier doesn't include a slot value. May be {@code null}
     * @return the value returned by {@code function}
     * @param <R> the type returned by {@code function}
     *
     * @deprecated use {@link JBossModulesNameUtil#parseModuleIdentifier(String, BiFunction, boolean, String)}
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public static <R> R parseModuleIdentifier(String moduleIdentifier, BiFunction<String, String, R> function,
                                              boolean canonicalize, String defaultSlot) {
        return JBossModulesNameUtil.parseModuleIdentifier(moduleIdentifier, function, canonicalize, defaultSlot);
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
     *
     * @deprecated use {@link JBossModulesNameUtil#canonicalModuleIdentifier(String, String)}
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public static String canonicalModuleIdentifier(String name, String slot) {
        return JBossModulesNameUtil.canonicalModuleIdentifier(name, slot);
    }

    /**
     * A {@link ParameterCorrector} that {@link #canonicalModuleIdentifier(String) canonicalizes}
     * values that are meant to represent JBoss Modules module names.
     *
     * @deprecated use {@link JBossModulesNameUtil#parseCanonicalModuleIdentifier} after resolving your attribute value in your step handler.
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
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
}