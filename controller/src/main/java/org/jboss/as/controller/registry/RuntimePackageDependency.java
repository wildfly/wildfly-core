/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.registry;

import java.util.Objects;

/**
 * A runtime package dependency expresses a dependency to a galleon package. A
 * RuntimePackageDependency models 3 different types of dependencies. 'required'
 * (needed to operate), optional (not required to operate) and passive (required
 * to operate only if all the dependencies of this dependency's package are
 * present).
 *
 * @author jdenise@redhat.com
 */
public final class RuntimePackageDependency {
    private enum TYPE {
        REQUIRED,
        OPTIONAL,
        PASSIVE
    }

    private final String name;
    private final TYPE type;

    private RuntimePackageDependency(String name, TYPE type) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        this.name = name;
        this.type = type;
    }

    /**
     * Get the package name.
     *
     * @return The package name
     */
    public String getName() {
        return name;
    }

    /**
     * Is this dependency optional. NB: passive is a special optional
     * dependency.
     *
     * @return true if dependency is optional or passive.
     */
    public boolean isOptional() {
        return type == TYPE.OPTIONAL || type == TYPE.PASSIVE;
    }

    /**
     * Is this dependency required.
     *
     * @return true if the dependency is required
     */
    public boolean isRequired() {
        return type == TYPE.REQUIRED;
    }

    /**
     * Is this dependency passive.
     *
     * @return true if the dependency is passive
     */
    public boolean isPassive() {
        return type == TYPE.PASSIVE;
    }

    /**
     * Build a passive RuntimePackageDependency.
     *
     * @param name Package name.
     * @return RuntimePackageDependency instance
     */
    public static RuntimePackageDependency passive(String name) {
        return new RuntimePackageDependency(name, TYPE.PASSIVE);
    }

    /**
     * Build a required RuntimePackageDependency.
     *
     * @param name Package name.
     * @return RuntimePackageDependency instance
     */
    public static RuntimePackageDependency required(String name) {
        return new RuntimePackageDependency(name, TYPE.REQUIRED);
    }

    /**
     * Build an optional RuntimePackageDependency.
     *
     * @param name Package name.
     * @return RuntimePackageDependency instance
     */
    public static RuntimePackageDependency optional(String name) {
        return new RuntimePackageDependency(name, TYPE.OPTIONAL);
    }
}
