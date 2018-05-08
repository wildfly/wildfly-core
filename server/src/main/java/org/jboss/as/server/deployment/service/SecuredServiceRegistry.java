/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
