/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.capability.Capability;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.RuntimePackageDependency;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
class ResourceBuilderRoot implements ResourceBuilder {
    private final PathElement pathElement;
    private final StandardResourceDescriptionResolver resourceResolver;
    private final List<AttributeBinding> attributes = new LinkedList<>();
    private final List<OperationBinding> operations = new LinkedList<>();
    private final List<Capability> capabilities = new LinkedList<>();
    private ResourceDescriptionResolver attributeResolver = null; // TODO field is never read except in copy c'tor
    private OperationStepHandler addHandler;
    private OperationStepHandler removeHandler;
    private DeprecationData deprecationData;
    private final List<ResourceBuilderRoot> children = new LinkedList<ResourceBuilderRoot>();
    private final ResourceBuilderRoot parent;
    private boolean isRuntime = false;
    private Set<RuntimeCapability> incorporatingCapabilities;
    private Set<CapabilityReferenceRecorder> requirements;
    private boolean isFeature = true;
    private final List<RuntimePackageDependency> additionalPackages = new LinkedList<>();

    /** Normal constructor */
    private ResourceBuilderRoot(PathElement pathElement, StandardResourceDescriptionResolver resourceResolver,
                                OperationStepHandler addHandler,
                                OperationStepHandler removeHandler,
                                ResourceBuilderRoot parent) {
        this.pathElement = pathElement;
        this.resourceResolver = resourceResolver;
        this.parent = parent;
        this.addHandler = addHandler;
        this.removeHandler = removeHandler;
    }

    /**
     * Copy constructor for {@link #pushChild(ResourceBuilder)} to use when integrating a child built externally.
     */
    private ResourceBuilderRoot(final ResourceBuilderRoot toCopy, final ResourceBuilderRoot parent) {
        this(toCopy.pathElement, toCopy.resourceResolver, toCopy.addHandler, toCopy.removeHandler, parent);
        this.attributes.addAll(toCopy.attributes);
        this.operations.addAll(toCopy.operations);
        this.capabilities.addAll(toCopy.capabilities);
        this.children.addAll(toCopy.children);
        if (toCopy.incorporatingCapabilities != null) {
            this.incorporatingCapabilities = new HashSet<>(toCopy.incorporatingCapabilities);
        }
        if (toCopy.requirements != null) {
            this.requirements = new HashSet<>(toCopy.requirements);
        }
        this.addHandler = toCopy.addHandler;
        this.removeHandler = toCopy.removeHandler;
        this.deprecationData = toCopy.deprecationData;
        this.isRuntime = parent.isRuntime;
        this.isFeature = parent.isFeature;
        this.attributeResolver = toCopy.attributeResolver; // TODO Remove if this field is unneeded
        this.additionalPackages.addAll(toCopy.additionalPackages);
    }

    static ResourceBuilder create(PathElement pathElement, StandardResourceDescriptionResolver resourceDescriptionResolver) {
        return new ResourceBuilderRoot(pathElement, resourceDescriptionResolver, null, null, null);
    }

    static ResourceBuilder create(PathElement pathElement, StandardResourceDescriptionResolver resourceResolver,
                                  OperationStepHandler addHandler,
                                  OperationStepHandler removeHandler) {
        return new ResourceBuilderRoot(pathElement, resourceResolver, addHandler, removeHandler, null);
    }


    @Override
    public ResourceBuilder setAddOperation(final AbstractAddStepHandler handler) {
        this.addHandler = handler;
        return this;
    }

    @Override
    public ResourceBuilder setRemoveOperation(final AbstractRemoveStepHandler handler) {
        this.removeHandler = handler;
        return this;
    }

    @Override
    public ResourceBuilder setAddOperation(RestartParentResourceAddHandler handler) {
        this.addHandler = handler;
        return this;
    }

    @Override
    public ResourceBuilder setRemoveOperation(RestartParentResourceRemoveHandler handler) {
        this.removeHandler = handler;
        return this;
    }

    @Override
    public ResourceBuilder addReadWriteAttribute(AttributeDefinition attributeDefinition, OperationStepHandler reader, OperationStepHandler writer) {
        attributes.add(new AttributeBinding(attributeDefinition, reader, writer, AttributeAccess.AccessType.READ_WRITE));
        return this;
    }

    @Override
    public ResourceBuilder addReadWriteAttributes(OperationStepHandler reader, OperationStepHandler writer, final AttributeDefinition... attributes) {
        for (AttributeDefinition ad : attributes) {
            this.attributes.add(new AttributeBinding(ad, reader, writer, AttributeAccess.AccessType.READ_WRITE));
        }
        return this;
    }


    @Override
    public ResourceBuilder addReadOnlyAttribute(AttributeDefinition attributeDefinition) {
        attributes.add(new AttributeBinding(attributeDefinition, null, null, AttributeAccess.AccessType.READ_ONLY));
        return this;
    }

    @Override
    public ResourceBuilder addMetric(AttributeDefinition attributeDefinition, OperationStepHandler handler) {
        attributes.add(new AttributeBinding(attributeDefinition, handler, null, AttributeAccess.AccessType.METRIC));
        return this;
    }

    @Override
    public ResourceBuilder addMetrics(OperationStepHandler metricHandler, final AttributeDefinition... attributes) {
        for (AttributeDefinition ad : attributes) {
            this.attributes.add(new AttributeBinding(ad, metricHandler, null, AttributeAccess.AccessType.METRIC));
        }
        return this;
    }

    @Override
    public ResourceBuilder setAttributeResolver(ResourceDescriptionResolver resolver) {
        this.attributeResolver = resolver;
        return this;
    }

    @Override
    public ResourceBuilder addOperation(final OperationDefinition operationDefinition, final OperationStepHandler handler) {

        return addOperation(operationDefinition, handler, false);
    }

    @Override
    public ResourceBuilder addOperation(final OperationDefinition operationDefinition, final OperationStepHandler handler, boolean inherited) {
        operations.add(new OperationBinding(operationDefinition, handler, inherited));
        return this;
    }

    @Override
    public ResourceBuilder deprecated(ModelVersion since) {
        this.deprecationData = new DeprecationData(since);
        return this;
    }

    @Override
    public ResourceBuilder setRuntime() {
        this.isRuntime = true;
        return this;
    }

    @Override
    public ResourceBuilder noFeature() {
        this.isFeature = false;
        return this;
    }

    @Override
    public ResourceBuilder addCapability(Capability capability) {
        capabilities.add(capability);
        return this;
    }

    @Override
    public ResourceBuilder addCapabilities(Capability... capabilities) {
        this.capabilities.addAll(Arrays.asList(capabilities));
        return this;
    }

    @Override
    public ResourceBuilder setIncorporatingCapabilities(Set<RuntimeCapability> incorporating) {
        this.incorporatingCapabilities = incorporating;
        return this;
    }

    @Override
    public ResourceBuilder setRequirements(Set<CapabilityReferenceRecorder> requirements) {
        this.requirements = requirements;
        return this;
    }

    @Override
    public ResourceBuilder pushChild(final PathElement pathElement) {
        return pushChild(pathElement, resourceResolver.getChildResolver(pathElement.getKey()));
    }

    @Override
    public ResourceBuilder pushChild(final PathElement pathElement, final OperationStepHandler addHandler, final OperationStepHandler removeHandler) {
        return pushChild(pathElement, resourceResolver.getChildResolver(pathElement.getKey()), addHandler, removeHandler);
    }

    @Override
    public ResourceBuilder pushChild(final PathElement pathElement, StandardResourceDescriptionResolver resolver) {
        return pushChild(pathElement, resolver, null, null);
    }

    @Override
    public ResourceBuilder pushChild(final PathElement pathElement, StandardResourceDescriptionResolver resolver, final OperationStepHandler addHandler, final OperationStepHandler removeHandler) {
        ResourceBuilderRoot child = new ResourceBuilderRoot(pathElement, resolver, addHandler, removeHandler, this);
        children.add(child);
        return child;
    }

    @Override
    public ResourceBuilder pushChild(final ResourceBuilder child) {
        ResourceBuilderRoot childDelegate = new ResourceBuilderRoot((ResourceBuilderRoot) child, this);
        children.add(childDelegate);
        return childDelegate;
    }

    @Override
    public ResourceBuilder pop() {
        if (parent == null) {
            return this;
        }
        return parent;
    }

    @Override
    public ResourceBuilder addAdditionalRuntimePackages(RuntimePackageDependency... additionalPackages) {
        this.additionalPackages.addAll(Arrays.asList(additionalPackages));
        return this;
    }

    @Override
    public ResourceDefinition build() {
        if (parent != null) {
            return parent.build();
        }
        return new BuilderResourceDefinition(this);
    }

    List<AttributeBinding> getAttributes() {
        return attributes;
    }

    List<OperationBinding> getOperations() {
        return operations;
    }

    List<ResourceBuilderRoot> getChildren() {
        return children;
    }

    private static class BuilderResourceDefinition extends SimpleResourceDefinition {
        final ResourceBuilderRoot builder;

        private BuilderResourceDefinition(ResourceBuilderRoot builder) {
            //super(builder.pathElement, builder.resourceResolver, builder.addHandler, builder.removeHandler, null, null, builder.deprecationData, builder.isRuntime);
            super(new Parameters(builder.pathElement, builder.resourceResolver)
            .setAddHandler(builder.addHandler)
                    .setRemoveHandler(builder.removeHandler)
                    .setDeprecationData(builder.deprecationData)
                    .setRuntime(builder.isRuntime)
                    .setCapabilities(builder.capabilities.toArray(new RuntimeCapability[builder.capabilities.size()]))
                    .setAdditionalPackages(builder.additionalPackages.toArray(new RuntimePackageDependency[builder.additionalPackages.size()]))
                    .setIncorporatingCapabilities(builder.incorporatingCapabilities)
                    .setFeature(builder.isFeature)
            );
            this.builder = builder;
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            for (OperationBinding ob : builder.operations) {
                ob.register(resourceRegistration);
            }
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
            for (AttributeBinding ab : builder.attributes) {
                ab.register(resourceRegistration);
            }
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            super.registerChildren(resourceRegistration);
            for (ResourceBuilderRoot child : builder.children) {
                resourceRegistration.registerSubModel(new BuilderResourceDefinition(child));
            }
        }
    }

    private static final class AttributeBinding {
        private final AttributeDefinition attribute;
        private final OperationStepHandler readOp;
        private final OperationStepHandler writeOp;
        private final AttributeAccess.AccessType accessType;

        AttributeBinding(AttributeDefinition attribute, OperationStepHandler readOp, OperationStepHandler writeOp, AttributeAccess.AccessType accessType) {
            this.attribute = attribute;
            this.readOp = readOp;
            this.writeOp = writeOp;
            this.accessType = accessType;
        }

        void register(ManagementResourceRegistration registration) {
            if (accessType == AttributeAccess.AccessType.READ_ONLY) {
                registration.registerReadOnlyAttribute(attribute, readOp);
            } else if (accessType == AttributeAccess.AccessType.READ_WRITE) {
                registration.registerReadWriteAttribute(attribute, readOp, writeOp);
            } else if (accessType == AttributeAccess.AccessType.METRIC) {
                registration.registerMetric(attribute, readOp);
            }
        }

    }

    private static final class OperationBinding {
        private OperationDefinition definition;
        private OperationStepHandler handler;
        private boolean inherited;

        OperationBinding(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {
            this.definition = definition;
            this.handler = handler;
            this.inherited = inherited;
        }

        public void register(ManagementResourceRegistration registration) {
            registration.registerOperationHandler(definition, handler, inherited);
        }
    }

}
