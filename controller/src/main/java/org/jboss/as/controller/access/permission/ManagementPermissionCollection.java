/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.permission;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.access.Action;

/**
* {@link PermissionCollection} for use with {@link ManagementPermission}. It's homogeneous.
*
* @author Brian Stansberry (c) 2013 Red Hat Inc.
*/
public class ManagementPermissionCollection extends PermissionCollection {

    private final Class<? extends ManagementPermission> type;

    private final String name;
    private final Map<Action.ActionEffect, ManagementPermission> permissions = new HashMap<Action.ActionEffect, ManagementPermission>();

    public ManagementPermissionCollection(Class<? extends ManagementPermission> type) {
        this(null, type);
    }

    public ManagementPermissionCollection(String name, Class<? extends ManagementPermission> type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public void add(Permission permission) {
        if (isReadOnly()) {
            throw ControllerLogger.ROOT_LOGGER.permissionCollectionIsReadOnly();
        }

        if (type.equals(permission.getClass())) {
            ManagementPermission mperm = (ManagementPermission) permission;
            synchronized (permissions) {
                permissions.put(mperm.getActionEffect(), mperm);
            }
        } else {
            throw ControllerLogger.ROOT_LOGGER.incompatiblePermissionType(permission.getClass());
        }
    }

    @Override
    public boolean implies(Permission permission) {
        if (permission instanceof ManagementPermission) {
            ManagementPermission mperm = (ManagementPermission) permission;
            Action.ActionEffect actionEffect = mperm.getActionEffect();
            ManagementPermission provided;
            synchronized (permissions) {
                provided = permissions.get(actionEffect);
            }
            if (provided == null) {
                ControllerLogger.ACCESS_LOGGER.tracef("Permission collection '%s' does not provide a permission for %s", name, actionEffect);
                return false;
            } else if (!provided.implies(mperm)) {
                ControllerLogger.ACCESS_LOGGER.tracef("Permission provided in collection '%s' for action %s does not imply the requested permission", name, actionEffect);
                return false;
            }
            return true;
        }
        ControllerLogger.ACCESS_LOGGER.tracef("Permission collection %s does not imply %s as it is not a ManagementPermission", name, permission);
        return false;
    }

    @Override
    public Enumeration<Permission> elements() {
        final Iterator<ManagementPermission> iterator = iterator();
        return new Enumeration<Permission>() {
            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public Permission nextElement() {
                return iterator.next();
            }
        };
    }

    public String getName() {
        return name;
    }

    private Iterator<ManagementPermission> iterator() {
        synchronized (permissions) {
            return permissions.values().iterator();
        }
    }
}
