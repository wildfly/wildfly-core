/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.permission;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;

/**
 * {@link PermissionCollection} that implies all permissions.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public final class AllPermissionsCollection extends PermissionCollection {

    public static final AllPermissionsCollection INSTANCE = new AllPermissionsCollection();

    private AllPermissionsCollection() {
    }

    @Override
    public void add(Permission permission) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean implies(Permission permission) {
        return true;
    }

    @Override
    public Enumeration<Permission> elements() {
        throw new UnsupportedOperationException();
    }
}
