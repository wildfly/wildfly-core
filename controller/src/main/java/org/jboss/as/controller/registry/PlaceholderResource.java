/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Resource that solely holds a place in the resource tree and has no model or children. A typically usage
 * would be for a resource that represents some runtime-only service, where all attributes of the
 * resource are read by reading that service.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class PlaceholderResource implements Resource {

    private static final ModelNode MODEL = new ModelNode();
    public static final PlaceholderResource INSTANCE = new PlaceholderResource();

    static {
        MODEL.protect();
    }

    private PlaceholderResource() {
    }

    @Override
    public ModelNode getModel() {
        return MODEL;
    }

    @Override
    public void writeModel(ModelNode newModel) {
        throw ControllerLogger.ROOT_LOGGER.immutableResource();
    }

    @Override
    public boolean isModelDefined() {
        return false;
    }

    @Override
    public boolean hasChild(PathElement element) {
        return false;
    }

    @Override
    public Resource getChild(PathElement element) {
        return null;
    }

    @Override
    public Resource requireChild(PathElement element) {
        throw new NoSuchResourceException(element);
    }

    @Override
    public boolean hasChildren(String childType) {
        return false;
    }

    @Override
    public Resource navigate(PathAddress address) {
        return Resource.Tools.navigate(this, address);
    }

    @Override
    public Set<String> getChildTypes() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getChildrenNames(String childType) {
        return Collections.emptySet();
    }

    @Override
    public Set<ResourceEntry> getChildren(String childType) {
        return Collections.emptySet();
    }

    @Override
    public void registerChild(PathElement address, Resource resource) {
        throw ControllerLogger.ROOT_LOGGER.immutableResource();
    }

    @Override
    public void registerChild(PathElement address, int index, Resource resource) {
        throw ControllerLogger.ROOT_LOGGER.immutableResource();
    }

    @Override
    public Resource removeChild(PathElement address) {
        return null;
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
    public Set<String> getOrderedChildTypes() {
        return Collections.emptySet();
    }

    @Override
    public Resource clone() {
        return INSTANCE;
    }

    public static class PlaceholderResourceEntry extends PlaceholderResource implements ResourceEntry {

        final PathElement path;

        public PlaceholderResourceEntry(final PathElement path) {
            this.path = path;
        }

        public PlaceholderResourceEntry(final String type, final String name) {
            this.path = PathElement.pathElement(type, name);
        }

        @Override
        public String getName() {
            return path.getValue();
        }

        @Override
        public PathElement getPathElement() {
            return path;
        }

        @Override
        public PlaceholderResourceEntry clone() {
            return this;
        }

        @Override
        public int hashCode() {
            return this.path.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof ResourceEntry)) return false;
            return this.path.equals(((ResourceEntry) object).getPathElement());
        }
    }
}
