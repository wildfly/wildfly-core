/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.security.Permission;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.security.LoadedPermissionFactory;
import org.jboss.modules.security.ModularPermissionFactory;
import org.jboss.modules.security.PermissionFactory;
import org.wildfly.extension.security.manager.logging.SecurityManagerLogger;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * <p>A deferred permission factory that logs a warning if the construction fails.</p>
 *
 * @author rmartinc
 */
public class DeferredPermissionFactory implements PermissionFactory {

    private final Type type;
    private final ModuleLoader moduleLoader;
    private final String moduleName;
    private final String permissionClass;
    private final String permissionName;
    private final String permissionActions;

    private volatile PermissionFactory factory;

    public enum Type {
        DEPLOYMENT,
        MAXIMUM_SET,
        MINIMUM_SET,
    }

    public DeferredPermissionFactory(Type type, ModuleLoader moduleLoader, String moduleName, String permissionClass, String permissionName, String permissionActions) {
        this.type = checkNotNullParam("type", type);
        this.moduleLoader = checkNotNullParam("moduleLoader", moduleLoader);
        this.moduleName = moduleName;
        this.permissionClass = checkNotNullParam("permissionClass", permissionClass);
        this.permissionName = permissionName;
        this.permissionActions = permissionActions;
        this.factory = null;
    }

    private PermissionFactory getFactory() {
        PermissionFactory local = this.factory;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            local = this.factory;
            if (local == null) {
                if (moduleName == null) {
                    local = new LoadedPermissionFactory(WildFlySecurityManager.getClassLoaderPrivileged(this.getClass()),
                            permissionClass, permissionName, permissionActions);
                } else {
                    local = new ModularPermissionFactory(moduleLoader, moduleName, permissionClass, permissionName, permissionActions);
                }
            }
            this.factory = local;
            return local;
        }
    }

    @Override
    public Permission construct() {
        Permission result = getFactory().construct();
        if (result == null) {
            SecurityManagerLogger.ROOT_LOGGER.ignoredPermission(type.name().replace("_", "-").toLowerCase(), permissionClass,
                    permissionName == null? "" : permissionName,
                    permissionActions == null? "" : permissionActions);
        }
        return result;
    }
}
