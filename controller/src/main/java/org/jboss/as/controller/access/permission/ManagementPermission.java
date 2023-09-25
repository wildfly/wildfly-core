/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.permission;

import java.security.Permission;
import java.security.PermissionCollection;

import org.jboss.as.controller.access.Action;

/**
 * Base class for {@link Permission} implementations related to WildFly access control.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public abstract class ManagementPermission extends Permission {

    private final Action.ActionEffect actionEffect;

    /**
     * Constructs a permission with the specified name and action effect.
     */
    ManagementPermission(String name, Action.ActionEffect actionEffect) {
        super(name);
        this.actionEffect = actionEffect;
    }

    @Override
    public PermissionCollection newPermissionCollection() {
        return new ManagementPermissionCollection(getClass());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ManagementPermission that = (ManagementPermission) o;

        return actionEffect == that.actionEffect;

    }

    @Override
    public int hashCode() {
        return actionEffect.hashCode();
    }

    @Override
    public String getActions() {
        return actionEffect.toString();
    }

    public Action.ActionEffect getActionEffect() {
        return actionEffect;
    }
}
