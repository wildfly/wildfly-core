/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.module.util;

import java.util.Locale;

/**
 * A module dependency used for the {@link ModuleBuilder}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ModuleDependency {

    private final String name;
    private final boolean export;
    private final boolean optional;
    private final Services services;

    public enum Services {
        NONE,
        IMPORT,
        EXPORT;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private ModuleDependency(final String name, final boolean export, final boolean optional, final Services services) {
        this.name = name;
        this.export = export;
        this.optional = optional;
        this.services = services;
    }

    /**
     * Creates a new dependency.
     *
     * @param name the name of the dependency
     *
     * @return the new dependency
     */
    public static ModuleDependency of(final String name) {
        return new ModuleDependency(name, false, false, null);
    }

    /**
     * Creates a new dependency.
     *
     * @param name   the name of the dependency
     * @param export {@code true} if the dependency should be exported
     *
     * @return the new dependency
     */
    public static ModuleDependency of(final String name, final boolean export) {
        return new ModuleDependency(name, export, false, null);
    }

    /**
     * Creates a new dependency.
     *
     * @param name     the name of the dependency
     * @param export   {@code true} if the dependency should be exported
     * @param optional {@code true} if the dependency should be optional
     *
     * @return the new dependency
     */
    public static ModuleDependency of(final String name, final boolean export, final boolean optional) {
        return new ModuleDependency(name, export, optional, null);
    }

    /**
     * Creates a new dependency.
     *
     * @param name     the name of the dependency
     * @param export   {@code true} if the dependency should be exported
     * @param optional {@code true} if the dependency should be optional
     * @param services the services value or {@code null} for the default
     *
     * @return the new dependency
     */
    public static ModuleDependency of(final String name, final boolean export, final boolean optional,
                                      final Services services) {
        return new ModuleDependency(name, export, optional, services);
    }

    /**
     * Returns the module dependency name.
     *
     * @return the dependency name
     */
    public String getName() {
        return name;
    }

    /**
     * Indicates if the dependency should be exported.
     *
     * @return whether or not the dependency should be exported
     */
    public boolean isExport() {
        return export;
    }

    /**
     * Indicates if the dependency is optional.
     *
     * @return whether or not the dependency is optional
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * Returns the services value or {@code null} for the default.
     *
     * @return the services value or {@code null} for the default
     */
    public Services getServices() {
        return services;
    }
}
