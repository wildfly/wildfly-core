/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.access;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;

/**
 * {@link org.jboss.as.controller.registry.Resource.NoSuchResourceException} variant
 * to throw when a resource should not be visible due to the called lacking
 * permissions to perform {@link org.jboss.as.controller.access.Action.ActionEffect#ADDRESS}.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public final class ResourceNotAddressableException extends Resource.NoSuchResourceException {

    public ResourceNotAddressableException(PathAddress pathAddress) {
        // Critical -- use the same message as the normal NoSuchResourceException so we don't leak data
        // to external users that this was an RBAC failure
        //noinspection ThrowableResultOfMethodCallIgnored
        super(ControllerLogger.ROOT_LOGGER.managementResourceNotFound(pathAddress).getMessage());
    }
}
