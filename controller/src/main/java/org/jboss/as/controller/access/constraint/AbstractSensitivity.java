/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import org.jboss.as.controller.access.Action;

/**
 * Configuration of sensitive data. Typically {@link org.jboss.as.controller.AttributeDefinition}, {@link org.jboss.as.controller.OperationDefinition}
 * and {@link org.jboss.as.controller.ResourceDefinition} will be annotated with zero or more
 * {@link org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition} containing this information. The purpose of this
 * class is to establish a default behaviour regarding sensitivity for
 * <ul>
 *      <li><b>access</b> - to be able to even be aware of the target's existence</li>
 *      <li><b>read</b> - to be able to read the target's data</li>
 *      <li><b>write</b> - to be able to write to the target</li>
 * </ul>
 * when registering a resource, attribute or operation. This default behaviour can then be tweaked.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class AbstractSensitivity {

    /** If {@code true} access (awareness) is considered sensitive by default*/
    private final boolean defaultRequiresAccessPermission;
    /** If {@code true} reading is considered sensitive by default*/
    private final boolean defaultRequiresReadPermission;
    /** If {@code true} writing is considered sensitive by default*/
    private final boolean defaultRequiresWritePermission;
    private volatile Boolean configuredRequiresAccessPermission;
    private volatile Boolean configuredRequiresReadPermission;
    private volatile Boolean configuredRequiresWritePermission;

    protected AbstractSensitivity(boolean defaultRequiresAccessPermission, boolean defaultRequiresReadPermission, boolean defaultRequiresWritePermission) {
        this.defaultRequiresAccessPermission = defaultRequiresAccessPermission;
        this.defaultRequiresReadPermission = defaultRequiresReadPermission;
        this.defaultRequiresWritePermission = defaultRequiresWritePermission;
    }

    public boolean isDefaultRequiresAccessPermission() {
        return defaultRequiresAccessPermission;
    }

    public boolean isDefaultRequiresReadPermission() {
        return defaultRequiresReadPermission;
    }

    public boolean isDefaultRequiresWritePermission() {
        return defaultRequiresWritePermission;
    }

    public boolean getRequiresAccessPermission() {
        final Boolean requires = configuredRequiresAccessPermission;
        return requires == null ? defaultRequiresAccessPermission : requires;
    }

    public Boolean getConfiguredRequiresAccessPermission() {
        return configuredRequiresAccessPermission;
    }

    public void setConfiguredRequiresAccessPermission(Boolean requiresAccessPermission) {
        this.configuredRequiresAccessPermission = requiresAccessPermission;
    }

    public boolean getRequiresReadPermission() {
        final Boolean requires = configuredRequiresReadPermission;
        return requires == null ? defaultRequiresReadPermission : requires;
    }

    public Boolean getConfiguredRequiresReadPermission() {
        return configuredRequiresReadPermission;
    }

    public void setConfiguredRequiresReadPermission(Boolean requiresReadPermission) {
        this.configuredRequiresReadPermission = requiresReadPermission;
    }

    public boolean getRequiresWritePermission() {
        final Boolean requires = configuredRequiresWritePermission;

        return requires == null ? defaultRequiresWritePermission : requires;
    }

    public Boolean getConfiguredRequiresWritePermission() {
        return configuredRequiresWritePermission;
    }

    public boolean isSensitive(Action.ActionEffect actionEffect) {
        if (actionEffect == Action.ActionEffect.ADDRESS) {
            return getRequiresAccessPermission();
        } else if (actionEffect == Action.ActionEffect.READ_CONFIG || actionEffect == Action.ActionEffect.READ_RUNTIME) {
            return getRequiresReadPermission();
        } else {
            return getRequiresWritePermission();
        }
    }

    public void setConfiguredRequiresWritePermission(Boolean requiresWritePermission) {
        this.configuredRequiresWritePermission = requiresWritePermission;
    }

    protected boolean isCompatibleWith(AbstractSensitivity other) {
        return !equals(other) ||
                (defaultRequiresAccessPermission == other.defaultRequiresAccessPermission
                        && defaultRequiresReadPermission == other.defaultRequiresReadPermission
                        && defaultRequiresWritePermission == other.defaultRequiresWritePermission);
    }

    public boolean isConfiguredRequiresAccessPermissionValid(Boolean requiresAccessPermission) {
        boolean effectiveAccessPermission = requiresAccessPermission == null ? defaultRequiresAccessPermission : requiresAccessPermission;
        boolean effectiveReadPermission = configuredRequiresReadPermission == null ? defaultRequiresReadPermission : configuredRequiresReadPermission;
        boolean effectiveWritePermission = configuredRequiresWritePermission == null ? defaultRequiresWritePermission : configuredRequiresWritePermission;
        if (effectiveAccessPermission == true && (effectiveReadPermission == false || effectiveWritePermission == false)) {
            return false;
        } else {
            return true;
        }
    }

    public boolean isConfiguredRequiresReadPermissionValid(Boolean requiresReadPermission) {
        boolean effectiveReadPermission = requiresReadPermission == null ? defaultRequiresReadPermission : requiresReadPermission;
        boolean effectiveAccessPermission = configuredRequiresAccessPermission == null ? defaultRequiresAccessPermission : configuredRequiresAccessPermission;
        boolean effectiveWritePermission = configuredRequiresWritePermission == null ? defaultRequiresWritePermission : configuredRequiresWritePermission;
        if (effectiveReadPermission == false && effectiveAccessPermission == true) {
            // write false to configured-requires-read while configured-requires-access is true is invalid
            return false;
        } else if (effectiveReadPermission == true && effectiveWritePermission == false) {
            // write true to configured-requires-read while configured-requires-write is false is invalid
            return false;
        } else {
            return true;
        }
    }

    public boolean isConfiguredRequiresWritePermissionValid(Boolean requiresWritePermission) {
        boolean effectiveWritePermission = requiresWritePermission == null ? defaultRequiresWritePermission : requiresWritePermission;
        boolean effectiveAccessPermission = configuredRequiresAccessPermission == null ? defaultRequiresAccessPermission : configuredRequiresAccessPermission;
        boolean effectiveReadPermission = configuredRequiresReadPermission == null ? defaultRequiresReadPermission : configuredRequiresReadPermission;
        if (effectiveWritePermission == false && (effectiveAccessPermission == true || effectiveReadPermission == true)) {
            // write false to configured-requires-write while configured-requires-access or configured-requires-read is true is invalid
            return false;
        } else {
            return true;
        }
    }
}
