/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.registry;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

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
        this.name = checkNotNullParamWithNullPointerException("name", name);
        this.type = checkNotNullParamWithNullPointerException("type", type);
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

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.name);
        hash = 71 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RuntimePackageDependency other = (RuntimePackageDependency) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "RuntimePackageDependency{" + "name=" + name + ", type=" + type + '}';
    }
}
