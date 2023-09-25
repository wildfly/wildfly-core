/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server;

import java.security.Permission;
import java.security.PrivilegedAction;

import org.jboss.as.server.security.ServerPermission;
import org.jboss.msc.service.ServiceContainer;

/**
 * Class that provides static access to the current servers ServiceRegistry.
 * <p/>
 * This is not ideal, however there are some places that require access to this
 * where there is no other way of getting it.
 *
 * @author Stuart Douglas
 */
public class CurrentServiceContainer {

    public static final PrivilegedAction<ServiceContainer> GET_ACTION = new PrivilegedAction<ServiceContainer>() {
        @Override
        public ServiceContainer run() {
            return getServiceContainer();
        }
    };

    private static volatile ServiceContainer serviceContainer;

    public static ServiceContainer getServiceContainer() {
        checkPermission(ServerPermission.GET_CURRENT_SERVICE_CONTAINER);
        return serviceContainer;
    }

    static void setServiceContainer(final ServiceContainer serviceContainer) {
        checkPermission(ServerPermission.SET_CURRENT_SERVICE_CONTAINER);
        CurrentServiceContainer.serviceContainer = serviceContainer;
    }

    private static void checkPermission(final Permission permission) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(permission);
        }
    }
}
