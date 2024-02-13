/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.version.Stability;

/**
* @author Tomaz Cerar (c) 2015 Red Hat Inc.
*/
public class DelegatingResourceDefinition extends ProvidedResourceDefinition {
    private final AtomicReference<ResourceDefinition> reference;

    public DelegatingResourceDefinition() {
        this(new AtomicReference<>());
    }

    private DelegatingResourceDefinition(AtomicReference<ResourceDefinition> reference) {
        super(reference::get);
        this.reference = reference;
    }

    protected void setDelegate(ResourceDefinition delegate) {
        this.reference.set(delegate);
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        ResourceDefinition delegate = this.reference.get();
        return (delegate != null) ? delegate.getAccessConstraints() : List.of();
    }

    @Override
    public boolean isRuntime() {
        ResourceDefinition delegate = this.reference.get();
        return (delegate != null) ? delegate.isRuntime() : false;
    }

    @Override
    public boolean isOrderedChild() {
        ResourceDefinition delegate = this.reference.get();
        return (delegate != null) ? delegate.isOrderedChild() : false;
    }

    @Override
    public Stability getStability() {
        ResourceDefinition delegate = this.reference.get();
        return (delegate != null) ? delegate.getStability() : Stability.DEFAULT;
    }
}
