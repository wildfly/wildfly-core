/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

/**
* Unique identifier for an {@link AccessConstraintDefinition}.
*
* @author Brian Stansberry (c) 2013 Red Hat Inc.
*/
public class AccessConstraintKey {
    private final String type;
    private final boolean core;
    private final String subsystem;
    private final String name;

    public AccessConstraintKey(AccessConstraintDefinition definition) {
        this(definition.getType(), definition.isCore(), definition.getSubsystemName(), definition.getName());
    }

    public AccessConstraintKey(String type, boolean core, String subsystem, String name) {
        this.type = type;
        this.core = core;
        this.subsystem = core ? null :subsystem;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccessConstraintKey that = (AccessConstraintKey) o;

        return core == that.core
                && name.equals(that.name)
                && !(subsystem != null ? !subsystem.equals(that.subsystem) : that.subsystem != null)
                && type.equals(that.type);

    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{type=" + type + ",core=" + core + ",subsystem=" + subsystem + ",name=" + name + '}';
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (core ? 1 : 0);
        result = 31 * result + (subsystem != null ? subsystem.hashCode() : 0);
        result = 31 * result + name.hashCode();
        return result;
    }

    public String getType() {
        return type;
    }

    public boolean isCore() {
        return core;
    }

    public String getSubsystemName() {
        return subsystem;
    }

    public String getName() {
        return name;
    }
}
