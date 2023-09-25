/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import javax.management.ObjectName;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;

class RootResourceIterator<T> {
    private final ResourceAccessControlUtil accessControlUtil;
    private final Resource rootResource;
    private final ResourceAction<T> action;

    RootResourceIterator(final ResourceAccessControlUtil accessControlUtil, final Resource rootResource, final ResourceAction<T> action) {
        this.accessControlUtil = accessControlUtil;
        this.rootResource = rootResource;
        this.action = action;
    }

    T iterate() {
        doIterate(rootResource, PathAddress.EMPTY_ADDRESS);
        return action.getResult();
    }

    private void doIterate(final Resource current, final PathAddress address) {
        boolean handleChildren = false;

        ObjectName resourceObjectName = action.onAddress(address);
        if (resourceObjectName != null &&
                (accessControlUtil == null || accessControlUtil.getResourceAccess(address, false).isAccessibleResource())) {
            handleChildren = action.onResource(resourceObjectName);
        }

        if (handleChildren) {
            for (String type : current.getChildTypes()) {
                if (current.hasChildren(type)) {
                    for (ResourceEntry entry : current.getChildren(type)) {
                        final PathElement pathElement = entry.getPathElement();
                        final PathAddress childAddress = address.append(pathElement);
                        doIterate(entry, childAddress);
                    }
                }
            }
        }
    }


    interface ResourceAction<T> {
        /**
         * An address has been identified that possibly should be applied to onResource.
         * @param address the address
         * @return an ObjectName representation of the address, or {@code null} if neither the address nor
         *         any of its children are interesting to this ResourceAction.
         */
        ObjectName onAddress(PathAddress address);

        /**
         *
         * @param resourceObjectName the ObjectName returned by onAddress.
         * @return {@code true} if child resources are interesting to this ResourceAction.
         */
        boolean onResource(ObjectName resourceObjectName);

        /**
         * Gets the overall result after all resources have been processed.
         * @return the result
         */
        T getResult();
    }
}
