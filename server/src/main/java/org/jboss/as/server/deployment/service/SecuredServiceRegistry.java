/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.service;

import org.jboss.as.server.security.ServerPermission;
import org.jboss.msc.service.DelegatingServiceRegistry;
import org.jboss.msc.service.ServiceRegistry;

import java.security.Permission;

/**
 * A {@link org.jboss.as.server.security.ServerPermission} is needed to use
 * {@link org.jboss.as.server.deployment.service.SecuredServiceRegistry}, i.e. invoke its methods.
 * The name of the permission is "{@code useServiceRegistry}."
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class SecuredServiceRegistry extends DelegatingServiceRegistry {

    private static final Permission PERMISSION = ServerPermission.USE_SERVICE_REGISTRY;

    SecuredServiceRegistry(final ServiceRegistry delegate) {
        super(delegate);
    }

    @Override
    protected ServiceRegistry getDelegate() {
        final SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            // TODO: these checks should be part of MSC
            securityManager.checkPermission(PERMISSION);
        }
        return super.getDelegate();
    }

}
