/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.capability;

import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.ServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A {@link org.jboss.as.controller.CapabilityReferenceRecorder} whose requirement is specified as a {@link ServiceDescriptor}.
 * @author Paul Ferraro
 */
public interface CapabilityReferenceRecorder<T> extends org.jboss.as.controller.CapabilityReferenceRecorder {

    /**
     * Returns the dependent capability.
     * @return a capability
     */
    RuntimeCapability<Void> getDependent();

    /**
     * Returns the service descriptor required by the dependent capability.
     * @return a service descriptor
     */
    ServiceDescriptor<T> getRequirement();

    @Override
    default String getBaseRequirementName() {
        return this.getRequirement().getName();
    }

    @Override
    default String getBaseDependentName() {
        return this.getDependent().getName();
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, UnaryServiceDescriptor<T> requirement) {
        return new CapabilityServiceDescriptorReferenceRecorder<>(capability, requirement, List.of());
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * Parent reference name is taken from the {@link PathElement} associated with the resource with which this attribute is referenced.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, BinaryServiceDescriptor<T> requirement) {
        return of(capability, requirement, CapabilityServiceDescriptorReferenceRecorder.CHILD_PATH);
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * Parent reference name is taken from the {@link PathElement} returned by the specified resolver
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param parentPathResolver resolver of the path containing the first segment of the requirement name
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, BinaryServiceDescriptor<T> requirement, Function<PathAddress, PathElement> parentPathResolver) {
        return new CapabilityServiceDescriptorReferenceRecorder<>(capability, requirement, List.of(parentPathResolver));
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement
     * Parent reference name is taken from the value of the specified {@link AttributeDefinition}.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param parentAttribute the attribute from which the parent segment of the requirement name is derived
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, BinaryServiceDescriptor<T> requirement, AttributeDefinition parentAttribute) {
        return new CapabilityServiceDescriptorReferenceRecorder<>(capability, requirement, parentAttribute);
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * Parent reference name is taken from the {@link PathElement} associated with the resource with which this attribute is referenced.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, TernaryServiceDescriptor<T> requirement) {
        return of(capability, requirement, CapabilityServiceDescriptorReferenceRecorder.PARENT_PATH, CapabilityServiceDescriptorReferenceRecorder.CHILD_PATH);
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * Parent reference name is taken from the {@link PathElement} returned by the specified resolver
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param grandparentPathResolver resolver of the path containing the first segment of the requirement name
     * @param parentPathResolver resolver of the path containing the second segment of the requirement name
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, TernaryServiceDescriptor<T> requirement, Function<PathAddress, PathElement> grandparentPathResolver, Function<PathAddress, PathElement> parentPathResolver) {
        return new CapabilityServiceDescriptorReferenceRecorder<>(capability, requirement, List.of(grandparentPathResolver, parentPathResolver));
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement
     * Parent reference name is taken from the value of the specified {@link AttributeDefinition}.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param grandparentAttribute the attribute from which the first segment of the requirement name is derived
     * @param parentAttribute the attribute from which the second segment of the requirement name is derived
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, TernaryServiceDescriptor<T> requirement, AttributeDefinition grandparentAttribute, AttributeDefinition parentAttribute) {
        return new CapabilityServiceDescriptorReferenceRecorder<>(capability, requirement, grandparentAttribute, parentAttribute);
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * Parent reference name is taken from the {@link PathElement} associated with the resource with which this attribute is referenced.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, QuaternaryServiceDescriptor<T> requirement) {
        return of(capability, requirement, CapabilityServiceDescriptorReferenceRecorder.GRANDPARENT_PATH, CapabilityServiceDescriptorReferenceRecorder.PARENT_PATH, CapabilityServiceDescriptorReferenceRecorder.CHILD_PATH);
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * Parent reference name is taken from the {@link PathElement} returned by the specified resolver
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param greatGrandparentPathResolver resolver of the path containing the first segment of the requirement name
     * @param grandparentPathResolver resolver of the path containing the second segment of the requirement name
     * @param parentPathResolver resolver of the path containing the third segment of the requirement name
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, QuaternaryServiceDescriptor<T> requirement, Function<PathAddress, PathElement> greatGrandparentPathResolver, Function<PathAddress, PathElement> grandparentPathResolver, Function<PathAddress, PathElement> parentPathResolver) {
        return new CapabilityServiceDescriptorReferenceRecorder<>(capability, requirement, List.of(greatGrandparentPathResolver, grandparentPathResolver, parentPathResolver));
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement
     * Parent reference name is taken from the value of the specified {@link AttributeDefinition}.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param greatGrandparentAttribute the attribute from which the first segment of the requirement name is derived
     * @param grandparentAttribute the attribute from which the second segment of the requirement name is derived
     * @param parentAttribute the attribute from which the third segment of the requirement name is derived
     */
    static <T> CapabilityReferenceRecorder<T> of(RuntimeCapability<Void> capability, QuaternaryServiceDescriptor<T> requirement, AttributeDefinition greatGrandparentAttribute, AttributeDefinition grandparentAttribute, AttributeDefinition parentAttribute) {
        return new CapabilityServiceDescriptorReferenceRecorder<>(capability, requirement, greatGrandparentAttribute, grandparentAttribute, parentAttribute);
    }

    abstract class AbstractCapabilityServiceDescriptorReferenceRecorder<T> implements CapabilityReferenceRecorder<T> {

        private final RuntimeCapability<Void> capability;
        private final ServiceDescriptor<T> requirement;

        AbstractCapabilityServiceDescriptorReferenceRecorder(RuntimeCapability<Void> capability, ServiceDescriptor<T> requirement) {
            this.capability = capability;
            this.requirement = requirement;
        }

        @Override
        public RuntimeCapability<Void> getDependent() {
            return this.capability;
        }

        @Override
        public ServiceDescriptor<T> getRequirement() {
            return this.requirement;
        }

        String resolveDependentName(OperationContext context) {
            return this.getDependent().fromBaseCapability(context.getCurrentAddress()).getName();
        }

        @Override
        public int hashCode() {
            return this.capability.getName().hashCode();
        }

        @Override
        public boolean equals(Object object) {
            return (object instanceof CapabilityReferenceRecorder) ? this.getDependent().equals(((CapabilityReferenceRecorder<?>) object).getDependent()) : false;
        }
    }

    class CapabilityServiceDescriptorReferenceRecorder<T> extends AbstractCapabilityServiceDescriptorReferenceRecorder<T> {
        private static final Function<PathAddress, PathElement> CHILD_PATH = PathAddress::getLastElement;
        private static final Function<PathAddress, PathElement> PARENT_PATH = CHILD_PATH.compose(PathAddress::getParent);
        private static final Function<PathAddress, PathElement> GRANDPARENT_PATH = PARENT_PATH.compose(PathAddress::getParent);

        private final BiFunction<OperationContext, String, String[]> requirementNameComposer;
        private final BiFunction<PathAddress, String, String[]> requirementPatternComposer;

        CapabilityServiceDescriptorReferenceRecorder(RuntimeCapability<Void> capability, ServiceDescriptor<T> requirement, List<Function<PathAddress, PathElement>> parentPathResolvers) {
            super(capability, requirement);
            this.requirementNameComposer = new BiFunction<>() {
                @Override
                public String[] apply(OperationContext context, String value) {
                    PathAddress address = context.getCurrentAddress();
                    String[] result = new String[parentPathResolvers.size() + 1];
                    ListIterator<Function<PathAddress, PathElement>> iterator = parentPathResolvers.listIterator();
                    while (iterator.hasNext()) {
                        result[iterator.nextIndex()] = iterator.next().apply(address).getValue();
                    }
                    result[parentPathResolvers.size()] = value;
                    return result;
                }
            };
            this.requirementPatternComposer = new BiFunction<>() {
                @Override
                public String[] apply(PathAddress address, String name) {
                    String[] result = new String[parentPathResolvers.size() + 1];
                    ListIterator<Function<PathAddress, PathElement>> iterator = parentPathResolvers.listIterator();
                    while (iterator.hasNext()) {
                        result[iterator.nextIndex()] = iterator.next().apply(address).getKey();
                    }
                    result[parentPathResolvers.size()] = name;
                    return result;
                }
            };
        }

        CapabilityServiceDescriptorReferenceRecorder(RuntimeCapability<Void> capability, ServiceDescriptor<T> requirement, AttributeDefinition... attributes) {
            super(capability, requirement);
            this.requirementNameComposer = new BiFunction<>() {
                @Override
                public String[] apply(OperationContext context, String value) {
                    String[] result = new String[attributes.length + 1];
                    if (attributes.length > 0) {
                        ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel();
                        for (int i = 0; i < attributes.length; ++i) {
                            result[i] = model.get(attributes[i].getName()).asString();
                        }
                    }
                    result[attributes.length] = value;
                    return result;
                }
            };
            this.requirementPatternComposer = new BiFunction<>() {
                @Override
                public String[] apply(PathAddress address, String name) {
                    String[] result = new String[attributes.length + 1];
                    for (int i = 0; i < attributes.length; ++i) {
                        result[i] = attributes[i].getName();
                    }
                    result[attributes.length] = name;
                    return result;
                }
            };
        }

        @Override
        public void addCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... values) {
            String dependentName = this.resolveDependentName(context);
            for (String value : values) {
                // Do not register requirement if undefined
                if (value != null) {
                    context.registerAdditionalCapabilityRequirement(this.resolveRequirementName(context, value), dependentName, attributeName);
                }
            }
        }

        @Override
        public void removeCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... values) {
            String dependentName = this.resolveDependentName(context);
            for (String value : values) {
                // We did not register a requirement if undefined
                if (value != null) {
                    context.deregisterCapabilityRequirement(this.resolveRequirementName(context, value), dependentName);
                }
            }
        }

        private String resolveRequirementName(OperationContext context, String value) {
            return RuntimeCapability.buildDynamicCapabilityName(this.getBaseRequirementName(), this.requirementNameComposer.apply(context, value));
        }

        @Override
        public String[] getRequirementPatternSegments(String name, PathAddress address) {
            return this.requirementPatternComposer.apply(address, name);
        }
    }
}
