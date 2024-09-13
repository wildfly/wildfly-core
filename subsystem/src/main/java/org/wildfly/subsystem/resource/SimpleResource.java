/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * An immutable resource facade for an existing leaf resource model, i.e. with no children.
 */
public class SimpleResource implements Resource {
    private final ModelNode model;

    public SimpleResource(ModelNode model) {
        this.model = model;
    }

    @Override
    public ModelNode getModel() {
        return this.model;
    }

    /**
     * @throws UnsupportedOperationException always.
     */
    @Override
    public void writeModel(ModelNode newModel) {
        if (newModel != this.model) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean isModelDefined() {
        return this.model.isDefined();
    }

    @Override
    public boolean hasChild(PathElement element) {
        return false;
    }

    @Override
    public Resource getChild(PathElement element) {
        return null;
    }

    /**
     * @throws UnsupportedOperationException always.
     */
    @Override
    public Resource requireChild(PathElement element) {
        throw new NoSuchResourceException(element);
    }

    @Override
    public boolean hasChildren(String childType) {
        return false;
    }

    /**
     * @throws UnsupportedOperationException if {@code address} is not empty;
     */
    @Override
    public Resource navigate(PathAddress address) {
        if (address.size() == 0) return this;
        throw new NoSuchResourceException(address.getElement(0));
    }

    @Override
    public Set<String> getChildTypes() {
        return Set.of();
    }

    @Override
    public Set<String> getChildrenNames(String childType) {
        return Set.of();
    }

    @Override
    public Set<ResourceEntry> getChildren(String childType) {
        return Set.of();
    }

    /**
     * @throws UnsupportedOperationException always.
     */
    @Override
    public void registerChild(PathElement address, Resource resource) {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException always.
     */
    @Override
    public void registerChild(PathElement address, int index, Resource resource) {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException always.
     */
    @Override
    public Resource removeChild(PathElement address) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getOrderedChildTypes() {
        return Set.of();
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
    public Resource clone() {
        return new SimpleResource(this.model.clone());
    }
}
