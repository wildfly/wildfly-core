/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.access;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Abstract superclass of {@link Resource} implementations for the platform mbean resources.
 */
abstract class AbstractClassificationResource implements Resource.ResourceEntry {

    private final PathElement pathElement;

    AbstractClassificationResource(PathElement pathElement) {
        this.pathElement = pathElement;
    }

    @Override
    public ModelNode getModel() {
        return new ModelNode();
    }

    @Override
    public void writeModel(ModelNode newModel) {
        // called in slave host boot; ignore this unless overridden
    }

    @Override
    public boolean isModelDefined() {
        return false;
    }

    @Override
    public Resource requireChild(PathElement address) {
        final Resource resource = getChild(address);
        if (resource == null) {
            throw new NoSuchResourceException(address);
        }
        return resource;
    }

    @Override
    public Resource navigate(PathAddress address) {
        if (address.size() == 0) {
            return this;
        } else {
            Resource child = requireChild(address.getElement(0));
            return address.size() == 1 ? child : child.navigate(address.subAddress(1));
        }
    }

    @Override
    public boolean hasChildren(String childType) {
        return getChildTypes().contains(childType);
    }

    @Override
    public boolean hasChild(PathElement element) {
        return getChildrenNames(element.getKey()).contains(element.getValue());
    }

    @Override
    public void registerChild(PathElement address, Resource resource) {
        //TODO i18n
        throw new IllegalStateException("Not writable");
    }

    @Override
    public void registerChild(PathElement address, int index, Resource resource) {
        //TODO i18n
        throw new UnsupportedOperationException("Not writable");
    }

    @Override
    public Resource removeChild(PathElement address) {
        //TODO i18n
        throw new IllegalStateException("Not writable");
    }

    @Override
    public boolean isRuntime() {
        return false;
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public Set<String> getOrderedChildTypes() {
        return Collections.emptySet();
    }

    @Override
    public Resource clone() {
        try {
            return Resource.class.cast(super.clone());
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("impossible");
        }
    }

    @Override
    public String getName() {
        return pathElement.getValue();
    }

    @Override
    public PathElement getPathElement() {
        return pathElement;
    }

    @Override
    public Resource getChild(PathElement element) {
        return getChildEntry(element.getKey(), element.getValue());
    }

    abstract ResourceEntry getChildEntry(String type, String name);

    public abstract Set<String> getChildrenNames(String type);

    public abstract Set<Resource.ResourceEntry> getChildren(String childType);

}
