/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.discovery;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 *
 * @author Alexey Loubyansky
 */
public class DiscoveryOptionsResource implements Resource {

    private final Resource delegate;

    public DiscoveryOptionsResource() {
        this(Resource.Factory.create());
    }

    DiscoveryOptionsResource(Resource delegate) {
        assert delegate != null : "delegate is null";

        this.delegate = delegate;
    }

    @Override
    public ModelNode getModel() {
        return delegate.getModel();
    }

    @Override
    public void writeModel(ModelNode newModel) {
        delegate.writeModel(newModel);
    }

    @Override
    public boolean isModelDefined() {
        return delegate.isModelDefined();
    }

    @Override
    public boolean hasChild(PathElement element) {
        return hasOption(element.getKey(), element.getValue());
    }

    @Override
    public Resource getChild(PathElement element) {
        return getOption(element.getKey(), element.getValue());
    }

    @Override
    public Resource requireChild(PathElement element) {
        final Resource option = getOption(element.getKey(), element.getValue());
        if (option == null) {
            throw new NoSuchResourceException(element);
        }
        return option;
    }

    @Override
    public boolean hasChildren(String childType) {
        return hasOption(childType);
    }

    @Override
    public Resource navigate(PathAddress address) {
        return Resource.Tools.navigate(this, address);
    }

    @Override
    public Set<String> getChildTypes() {
        return getOptionTypes();
    }

    @Override
    public Set<String> getChildrenNames(String childType) {
        return getOptionTypeNames(childType);
    }

    @Override
    public Set<ResourceEntry> getChildren(String childType) {
        return getOptions(childType);
    }

    @Override
    public void registerChild(PathElement address, Resource resource) {
    }

    @Override
    public void registerChild(PathElement address, int index, Resource resource) {
    }

    @Override
    public Resource removeChild(PathElement address) {
        return removeOption(address.getKey(), address.getValue());
    }

    @Override
    public boolean isRuntime() {
        return delegate.isRuntime();
    }

    @Override
    public boolean isProxy() {
        return delegate.isProxy();
    }

    @Override
    public Resource clone() {
        return new DiscoveryOptionsResource(delegate.clone());
    }

    protected Set<String> getOptionTypes() {
        final ModelNode options = delegate.getModel().get(ModelDescriptionConstants.OPTIONS);
        if (!options.isDefined()) {
            return Collections.emptySet();
        }
        final List<Property> list = options.asPropertyList();
        if (list.isEmpty()) {
            return Collections.emptySet();
        }
        final Set<String> types = new LinkedHashSet<String>(2);
        for (Property prop : list) {
            if (ModelDescriptionConstants.CUSTOM_DISCOVERY.equals(prop.getName())) {
                types.add(ModelDescriptionConstants.DISCOVERY_OPTION);
            } else {
                types.add(prop.getName());
            }
        }
        return types;
    }

    protected Set<String> getOptionTypeNames(String type) {
        final ModelNode options = delegate.getModel().get(ModelDescriptionConstants.OPTIONS);
        if (!options.isDefined()) {
            return Collections.emptySet();
        }
        final List<Property> list = options.asPropertyList();
        if (list.isEmpty()) {
            return Collections.emptySet();
        }
        final boolean staticOption = ModelDescriptionConstants.STATIC_DISCOVERY.equals(type);
        final Set<String> names = new LinkedHashSet<String>();
        for (Property prop : list) {
            if (staticOption) {
                if (prop.getName().equals(ModelDescriptionConstants.STATIC_DISCOVERY)) {
                    names.add(prop.getValue().get(ModelDescriptionConstants.NAME).asString());
                }
            } else if (prop.getName().equals(ModelDescriptionConstants.CUSTOM_DISCOVERY)) {
                names.add(prop.getValue().get(ModelDescriptionConstants.NAME).asString());
            }
        }
        return names;
    }

    protected Set<ResourceEntry> getOptions(String type) {
        final ModelNode options = delegate.getModel().get(ModelDescriptionConstants.OPTIONS);
        if (!options.isDefined()) {
            return Collections.emptySet();
        }
        final List<Property> list = options.asPropertyList();
        if (list.isEmpty()) {
            return Collections.emptySet();
        }
        final boolean staticOption = ModelDescriptionConstants.STATIC_DISCOVERY.equals(type);
        final Set<ResourceEntry> names = new LinkedHashSet<ResourceEntry>();
        for (Property prop : list) {
            if (staticOption) {
                if (prop.getName().equals(ModelDescriptionConstants.STATIC_DISCOVERY)) {
                    final Resource res = getResource(prop);
                    final String name = prop.getValue().get(ModelDescriptionConstants.NAME).asString();
                    names.add(entryFor(name, PathElement.pathElement(type, name), res));
                }
            } else if (prop.getName().equals(ModelDescriptionConstants.CUSTOM_DISCOVERY)) {
                final Resource res = getResource(prop);
                final String name = prop.getValue().get(ModelDescriptionConstants.NAME).asString();
                names.add(entryFor(name, PathElement.pathElement(ModelDescriptionConstants.DISCOVERY_OPTION, name), res));
            }
        }
        return names;
    }

    protected boolean hasOption(String type) {
        final ModelNode options = delegate.getModel().get(ModelDescriptionConstants.OPTIONS);
        if (!options.isDefined()) {
            return false;
        }
        final boolean staticOption = ModelDescriptionConstants.STATIC_DISCOVERY.equals(type);
        for (Property prop : options.asPropertyList()) {
            if (staticOption) {
                if (prop.getName().equals(ModelDescriptionConstants.STATIC_DISCOVERY)) {
                    return true;
                }
            } else if (prop.getName().equals(ModelDescriptionConstants.CUSTOM_DISCOVERY)) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasOption(String type, String name) {
        final ModelNode options = delegate.getModel().get(ModelDescriptionConstants.OPTIONS);
        if (!options.isDefined()) {
            return false;
        }
        final boolean staticOption = ModelDescriptionConstants.STATIC_DISCOVERY.equals(type);
        for (Property prop : options.asPropertyList()) {
            if (staticOption) {
                if (prop.getName().equals(ModelDescriptionConstants.STATIC_DISCOVERY) &&
                        prop.getValue().has(ModelDescriptionConstants.NAME) &&
                        prop.getValue().get(ModelDescriptionConstants.NAME).asString().equals(name)) {
                    return true;
                }
            } else if (prop.getName().equals(ModelDescriptionConstants.CUSTOM_DISCOVERY) &&
                    prop.getValue().has(ModelDescriptionConstants.NAME) &&
                    prop.getValue().get(ModelDescriptionConstants.NAME).asString().equals(name)) {
                return true;
            }
        }
        return false;
    }

    protected Resource getOption(String type, String name) {
        final ModelNode options = delegate.getModel().get(ModelDescriptionConstants.OPTIONS);
        if (!options.isDefined()) {
            return null;
        }
        final boolean staticOption = ModelDescriptionConstants.STATIC_DISCOVERY.equals(type);
        for (Property prop : options.asPropertyList()) {
            if (staticOption) {
                if (prop.getName().equals(ModelDescriptionConstants.STATIC_DISCOVERY)
                        && prop.getValue().has(ModelDescriptionConstants.NAME)
                        && prop.getValue().get(ModelDescriptionConstants.NAME).asString().equals(name)) {
                    return getResource(prop);
                }
            } else if (prop.getName().equals(ModelDescriptionConstants.CUSTOM_DISCOVERY) && prop.getValue().has(ModelDescriptionConstants.NAME)
                    && prop.getValue().get(ModelDescriptionConstants.NAME).asString().equals(name)) {
                return getResource(prop);
            }
        }
        return null;
    }

    protected Resource removeOption(String type, String name) {
        final ModelNode options = delegate.getModel().get(ModelDescriptionConstants.OPTIONS);
        if (!options.isDefined()) {
            return null;
        }
        final List<Property> list = options.asPropertyList();
        if (list.isEmpty()) {
            return null;
        }
        final boolean staticOption = ModelDescriptionConstants.STATIC_DISCOVERY.equals(type);
        Resource res = null;
        int i = 0;
        while (i < list.size() && res == null) {
            Property prop = list.get(i++);
            if (staticOption) {
                if (prop.getName().equals(ModelDescriptionConstants.STATIC_DISCOVERY)
                        && prop.getValue().has(ModelDescriptionConstants.NAME)
                        && prop.getValue().get(ModelDescriptionConstants.NAME).asString().equals(name)) {
                    res = getResource(prop);
                }
            } else if (prop.getName().equals(ModelDescriptionConstants.CUSTOM_DISCOVERY) && prop.getValue().has(ModelDescriptionConstants.NAME)
                    && prop.getValue().get(ModelDescriptionConstants.NAME).asString().equals(name)) {
                res = getResource(prop);
            }
        }
        if (res != null) {
            options.remove(i-1);
        }
        return res;
    }

    private Resource getResource(Property prop) {
        final Resource res = Resource.Factory.create(true);
        for(Property attr : prop.getValue().asPropertyList()) {
            if(!attr.getName().equals(ModelDescriptionConstants.NAME)) {
                res.getModel().get(attr.getName()).set(attr.getValue());
            }
        }
        return res;
    }

    static ResourceEntry entryFor(final String name, final PathElement pathElement, final Resource resource) {

        return new ResourceEntry() {

            @Override
            public ModelNode getModel() {
                return resource.getModel();
            }

            @Override
            public void writeModel(ModelNode newModel) {
                resource.writeModel(newModel);
            }

            @Override
            public boolean isModelDefined() {
                return resource.isModelDefined();
            }

            @Override
            public boolean hasChild(PathElement element) {
                return resource.hasChild(element);
            }

            @Override
            public Resource getChild(PathElement element) {
                return resource.getChild(element);
            }

            @Override
            public Resource requireChild(PathElement element) {
                return resource.requireChild(element);
            }

            @Override
            public boolean hasChildren(String childType) {
                return resource.hasChildren(childType);
            }

            @Override
            public Resource navigate(PathAddress address) {
                return resource.navigate(address);
            }

            @Override
            public Set<String> getChildTypes() {
                return resource.getChildTypes();
            }

            @Override
            public Set<String> getChildrenNames(String childType) {
                return resource.getChildrenNames(childType);
            }

            @Override
            public Set<ResourceEntry> getChildren(String childType) {
                return resource.getChildren(childType);
            }

            @Override
            public void registerChild(PathElement address, Resource resource) {
            }

            @Override
            public void registerChild(PathElement address, int index, Resource resource) {
            }

            @Override
            public Resource removeChild(PathElement address) {
                return resource.removeChild(address);
            }

            @Override
            public boolean isRuntime() {
                return resource.isRuntime();
            }

            @Override
            public boolean isProxy() {
                return resource.isProxy();
            }

            @Override
            public Resource clone() {
                return entryFor(name, pathElement, resource.clone());
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public PathElement getPathElement() {
                return pathElement;
            }

            @Override
            public Set<String> getOrderedChildTypes() {
                return resource.getOrderedChildTypes();
            }
        };
    }

    @Override
    public Set<String> getOrderedChildTypes() {
        return Collections.emptySet();
    }
}
