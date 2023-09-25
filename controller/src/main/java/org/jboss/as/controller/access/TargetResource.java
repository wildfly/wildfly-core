/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access;

import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;

/**
 * The resource that is the target of an action for which access control is needed.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public final class TargetResource {

    private final PathAddress address;
    private final ImmutableManagementResourceRegistration resourceRegistration;
    private final Resource resource;
    private final ServerGroupEffect serverGroupEffect;
    private final HostEffect hostEffect;
    private final List<AccessConstraintDefinition> accessConstraintDefinitions;


    public static TargetResource forStandalone(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration, Resource resource) {
        return new TargetResource(address, resourceRegistration, resource, null, null);
    }

    public static TargetResource forDomain(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration, Resource resource,
                                           ServerGroupEffect serverGroupEffect, HostEffect hostEffect) {
        return new TargetResource(address, resourceRegistration, resource, serverGroupEffect, hostEffect);
    }

    private TargetResource(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration,
                           Resource resource, ServerGroupEffect serverGroupEffect, HostEffect hostEffect) {
        this.address = address;
        this.resourceRegistration = resourceRegistration;
        this.resource = resource;
        this.serverGroupEffect = serverGroupEffect;
        this.hostEffect = hostEffect;
        if(resourceRegistration == null) {
            this.accessConstraintDefinitions = Collections.emptyList();
        } else {
            this.accessConstraintDefinitions = resourceRegistration.getAccessConstraints();
        }
    }

    public PathAddress getResourceAddress() {
        return address;
    }

    public ServerGroupEffect getServerGroupEffect() {
        return serverGroupEffect;
    }

    public HostEffect getHostEffect() {
        return hostEffect;
    }

    public List<AccessConstraintDefinition> getAccessConstraints() {
        return accessConstraintDefinitions;
    }

    public Resource getResource() {
        return resource;
    }

    public ImmutableManagementResourceRegistration getResourceRegistration() {
        return resourceRegistration;
    }

}
