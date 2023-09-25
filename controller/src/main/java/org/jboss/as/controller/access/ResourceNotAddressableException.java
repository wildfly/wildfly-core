/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
