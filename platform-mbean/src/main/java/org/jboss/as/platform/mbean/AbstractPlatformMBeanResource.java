/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.platform.mbean;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Abstract superclass of {@link Resource} implementations for the platform mbean resources.
 */
abstract class AbstractPlatformMBeanResource implements Resource.ResourceEntry {

    private final PathElement pathElement;

    AbstractPlatformMBeanResource(PathElement pathElement) {
        this.pathElement = pathElement;
    }

    @Override
    public ModelNode getModel() {
        return new ModelNode();
    }

    @Override
    public void writeModel(ModelNode newModel) {
        throw PlatformMBeanLogger.ROOT_LOGGER.modelNotWritable();
    }

    @Override
    public boolean isModelDefined() {
        return false;
    }

    @Override
    public Resource getChild(PathElement element) {
        if (hasChildren(element.getKey())) {
            return getChildEntry(element.getValue());
        }
        return null;
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
    public Set<Resource.ResourceEntry> getChildren(String childType) {
        if (!hasChildren(childType)) {
            return Collections.emptySet();
        } else {
            Set<Resource.ResourceEntry> result = new LinkedHashSet<ResourceEntry>();
            for (String name : getChildrenNames()) {
                result.add(getChildEntry(name));
            }
            return result;
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
    public Set<String> getChildrenNames(String childType) {
        if (hasChildren(childType)) {
            return getChildrenNames();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public void registerChild(PathElement address, Resource resource) {
        throw PlatformMBeanLogger.ROOT_LOGGER.addingChildrenNotSupported();
    }

    @Override
    public void registerChild(PathElement address, int index, Resource resource) {
        throw PlatformMBeanLogger.ROOT_LOGGER.addingChildrenNotSupported();
    }

    @Override
    public Resource removeChild(PathElement address) {
        throw PlatformMBeanLogger.ROOT_LOGGER.removingChildrenNotSupported();
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    @Override
    public boolean isProxy() {
        return false;
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
    public Set<String> getOrderedChildTypes() {
        return Collections.emptySet();
    }

    abstract ResourceEntry getChildEntry(String name);

    abstract Set<String> getChildrenNames();
}
