/*
* Copyright 2021 Red Hat, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.wildfly.extension.security.manager;

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
    };

    public DeferredPermissionFactory(Type type, ModuleLoader moduleLoader, String moduleName, String permissionClass, String permissionName, String permissionActions) {
        if (type == null) {
            throw new IllegalArgumentException("type argument is null");
        }
        if (moduleLoader == null) {
            throw new IllegalArgumentException("moduleLoader argument is null");
        }
        if (permissionClass == null) {
            throw new IllegalArgumentException("permissionClass argument is null");
        }
        this.type = type;
        this.moduleLoader = moduleLoader;
        this.moduleName = moduleName;
        this.permissionClass = permissionClass;
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
