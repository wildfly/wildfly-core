/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
* @author Tomaz Cerar (c) 2015 Red Hat Inc.
*/
public class DelegatingResourceDefinition implements ResourceDefinition {
    protected volatile ResourceDefinition delegate;

    protected void setDelegate(ResourceDefinition delegate) {
        this.delegate = delegate;
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        delegate.registerOperations(resourceRegistration);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        delegate.registerChildren(resourceRegistration);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        delegate.registerAttributes(resourceRegistration);
    }

    @Override
    public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
        delegate.registerNotifications(resourceRegistration);
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        delegate.registerCapabilities(resourceRegistration);
    }

    @Override
    public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
        delegate.registerAdditionalRuntimePackages(resourceRegistration);
    }

    @Override
    public boolean isFeature() {
        return delegate.isFeature();
    }

    @Override
    public int getMinOccurs() {
        return delegate.getMinOccurs();
    }

    @Override
    public int getMaxOccurs() {
        return delegate.getMaxOccurs();
    }

    @Override
    public PathElement getPathElement() {
        return delegate.getPathElement();
    }

    @Override
    public DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration resourceRegistration) {
        return delegate.getDescriptionProvider(resourceRegistration);
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        if (delegate == null) {
            return Collections.emptyList();
        }
        return delegate.getAccessConstraints();
    }

    @Override
    public boolean isRuntime() {
        if (delegate!=null) {
            return delegate.isRuntime();
        }
        return false;
    }

    @Override
    public boolean isOrderedChild() {
        if (delegate != null) {
            return delegate.isOrderedChild();
        }
        return false;
    }
}


