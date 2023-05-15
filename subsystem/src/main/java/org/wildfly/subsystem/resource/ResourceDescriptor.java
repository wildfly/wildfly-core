/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.capability.ResourceCapabilityReferenceRecorder;
import org.wildfly.subsystem.resource.operation.AddResourceOperationStepHandlerDescriptor;
import org.wildfly.subsystem.service.capability.RuntimeCapabilityProvider;

/**
 * An external description of a management resource, including its attributes, capabilities, etc.
 * @author Paul Ferraro
 */
public interface ResourceDescriptor extends AddResourceOperationStepHandlerDescriptor {

    /**
     * Returns a {@link ResourceDescriptor} builder.
     * The returned builder is not thread-safe and should not be modified by multiple threads.
     * @param resolver a description resolver for this resource
     * @return a description of this resource
     */
    static Builder builder(ResourceDescriptionResolver resolver) {
        return new Builder(resolver);
    }

    /**
     * Returns a convenience function that adds to a {@link Builder} the attributes provided by the specified enum class.
     * This is useful for consolidating resource registration for resources that provide a common capability, but with distinct attributes.
     * @param <E> a enumeration of attribute providers
     * @param enumClass a class defining an enumeration of attribute definition providers
     * @return a function providing additional configuration to an {@link Builder}
     */
    static <E extends Enum<E> & AttributeDefinitionProvider> UnaryOperator<Builder> provideAttributes(Class<E> enumClass) {
        return new UnaryOperator<>() {
            @Override
            public Builder apply(Builder builder) {
                return builder.provideAttributes(enumClass);
            }
        };
    }

    /**
     * Returns a convenience function that adds to a {@link Builder} the specified attributes.
     * This is useful for consolidating resource registration for resources that provide a common capability, but with distinct attributes.
     * @param attributes a collection of attributes
     * @return a function providing additional configuration to an {@link Builder}
     */
    static UnaryOperator<Builder> withAttributes(Collection<AttributeDefinition> attributes) {
        return new UnaryOperator<>() {
            @Override
            public Builder apply(Builder builder) {
                return builder.withAttributes(attributes);
            }
        };
    }

    class DefaultResourceDescriptor implements ResourceDescriptor {
        private final ResourceDescriptionResolver resolver;
        private final Map<RuntimeCapability<?>, Predicate<ModelNode>> capabilities;
        private final Collection<AttributeDefinition> attributes;
        private final Map<AttributeDefinition, OperationStepHandler> customAttributes;
        private final Collection<AttributeDefinition> ignoredAttributes;
        private final Set<PathElement> requiredChildren;
        private final Set<PathElement> requiredSingletonChildren;
        private final Map<AttributeDefinition, AttributeTranslation> attributeTranslations;
        private final Iterable<RuntimeResourceRegistrar> runtimeResourceRegistrars;
        private final Set<ResourceCapabilityReferenceRecorder<?>> resourceCapabilityReferences;
        private final UnaryOperator<OperationStepHandler> addOperationTransformer;
        private final UnaryOperator<OperationStepHandler> operationTransformer;
        private final UnaryOperator<Resource> resourceTransformer;
        private final Optional<Consumer<DeploymentProcessorTarget>> deploymentChainContributor;

        private DefaultResourceDescriptor(Builder builder) {
            this.resolver = builder.resolver;
            this.capabilities = builder.capabilities;
            this.attributes = builder.attributes;
            this.customAttributes = builder.customAttributes;
            this.ignoredAttributes = builder.ignoredAttributes;
            this.requiredChildren = builder.requiredChildren;
            this.requiredSingletonChildren = builder.requiredSingletonChildren;
            this.attributeTranslations = builder.attributeTranslations;
            this.runtimeResourceRegistrars = builder.runtimeResourceRegistrars;
            this.resourceCapabilityReferences = builder.resourceCapabilityReferences;
            this.addOperationTransformer = builder.addOperationTransformer;
            this.operationTransformer = builder.operationTransformer;
            this.resourceTransformer = builder.resourceTransformer;
            this.deploymentChainContributor = builder.deploymentChainContributor;
        }

        @Override
        public ResourceDescriptionResolver getResourceDescriptionResolver() {
            return this.resolver;
        }

        @Override
        public Map<RuntimeCapability<?>, Predicate<ModelNode>> getCapabilities() {
            return this.capabilities;
        }

        @Override
        public Iterable<RuntimeResourceRegistrar> getRuntimeResourceRegistrars() {
            return this.runtimeResourceRegistrars;
        }

        @Override
        public Set<ResourceCapabilityReferenceRecorder<?>> getResourceCapabilityReferences() {
            return this.resourceCapabilityReferences;
        }

        @Override
        public UnaryOperator<OperationStepHandler> getResourceOperationTransformation() {
            return this.operationTransformer;
        }

        @Override
        public Collection<AttributeDefinition> getAttributes() {
            return this.attributes;
        }

        @Override
        public Collection<AttributeDefinition> getIgnoredAttributes() {
            return this.ignoredAttributes;
        }

        @Override
        public Map<AttributeDefinition, OperationStepHandler> getCustomAttributes() {
            return this.customAttributes;
        }

        @Override
        public Set<PathElement> getRequiredChildren() {
            return this.requiredChildren;
        }

        @Override
        public Set<PathElement> getRequiredSingletonChildren() {
            return this.requiredSingletonChildren;
        }

        @Override
        public Map<AttributeDefinition, AttributeTranslation> getAttributeTranslations() {
            return this.attributeTranslations;
        }

        @Override
        public UnaryOperator<OperationStepHandler> getAddOperationTransformation() {
            return this.addOperationTransformer;
        }

        @Override
        public UnaryOperator<Resource> getResourceTransformation() {
            return this.resourceTransformer;
        }

        @Override
        public Optional<Consumer<DeploymentProcessorTarget>> getDeploymentChainContributor() {
            return this.deploymentChainContributor;
        }
    }

    /**
     * Builds a {@link ResourceDescriptor}.
     */
    static class Builder {
        private final ResourceDescriptionResolver resolver;
        private Map<RuntimeCapability<?>, Predicate<ModelNode>> capabilities = Map.of();
        private Collection<AttributeDefinition> attributes = List.of();
        private Map<AttributeDefinition, OperationStepHandler> customAttributes = Map.of();
        private Collection<AttributeDefinition> ignoredAttributes = List.of();
        private Set<PathElement> requiredChildren = Set.of();
        private Set<PathElement> requiredSingletonChildren = Set.of();
        private Map<AttributeDefinition, AttributeTranslation> attributeTranslations = Map.of();
        private Collection<RuntimeResourceRegistrar> runtimeResourceRegistrars = List.of();
        private Set<ResourceCapabilityReferenceRecorder<?>> resourceCapabilityReferences = Set.of();
        private UnaryOperator<OperationStepHandler> addOperationTransformer = UnaryOperator.identity();
        private UnaryOperator<OperationStepHandler> operationTransformer = UnaryOperator.identity();
        private UnaryOperator<Resource> resourceTransformer = UnaryOperator.identity();
        private Optional<Consumer<DeploymentProcessorTarget>> deploymentChainContributor = Optional.empty();

        Builder(ResourceDescriptionResolver resolver) {
            this.resolver = resolver;
        }

        public <E extends Enum<E> & AttributeDefinitionProvider> Builder provideAttributes(Class<E> providerClass) {
            return this.provideAttributes(EnumSet.allOf(providerClass));
        }

        public Builder provideAttributes(Set<? extends AttributeDefinitionProvider> providers) {
            this.attributes = this.collectAttributes(AttributeDefinitionProvider.stream(providers));
            return this;
        }

        public Builder withAttribute(AttributeDefinition attribute) {
            return this.addAttributes(List.of(attribute));
        }

        public Builder withAttributes(AttributeDefinition... attributes) {
            return this.addAttributes(List.of(attributes));
        }

        public Builder withAttributes(Collection<AttributeDefinition> attributes) {
            return this.addAttributes(Collections.unmodifiableCollection(attributes));
        }

        private Builder addAttributes(Collection<AttributeDefinition> attributes) {
            this.attributes = this.attributes.isEmpty() ? attributes  : this.collectAttributes(attributes.stream());
            return this;
        }

        private Collection<AttributeDefinition> collectAttributes(Stream<? extends AttributeDefinition> attributes) {
            return (this.attributes.isEmpty() ? attributes  : Stream.concat(this.attributes.stream(), attributes)).collect(Collectors.toUnmodifiableList());
        }

        public Builder ignoreAttribute(AttributeDefinition attribute) {
            return this.ignoreAttributes(List.of(attribute));
        }

        public Builder ignoreAttributes(AttributeDefinition... attributes) {
            return this.ignoreAttributes(List.of(attributes));
        }

        public Builder ignoreAttributes(Collection<AttributeDefinition> attributes) {
            this.addIgnoredAttributes(Collections.unmodifiableCollection(attributes));
            return this;
        }

        private Builder addIgnoredAttributes(Collection<AttributeDefinition> attributes) {
            this.ignoredAttributes = this.ignoredAttributes.isEmpty() ? attributes  : Stream.concat(this.ignoredAttributes.stream(), attributes.stream()).collect(Collectors.toUnmodifiableList());
            return this;
        }

        public Builder renameAttribute(AttributeDefinition attribute, AttributeDefinition targetAttribute) {
            return this.translateAttribute(attribute, AttributeTranslation.alias(targetAttribute));
        }

        public Builder translateAttribute(AttributeDefinition attribute, AttributeTranslation translation) {
            this.attributeTranslations = this.attributeTranslations.isEmpty() ? Map.of(attribute, translation) : Stream.concat(this.attributeTranslations.entrySet().stream(), this.attributeTranslations.entrySet().stream()).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            return this;
        }

        public <E extends Enum<E> & RuntimeCapabilityProvider<?>> Builder provideCapabilities(Class<E> providerClass) {
            return this.provideCapabilities(ModelNode::isDefined, providerClass);
        }

        public <E extends Enum<E> & RuntimeCapabilityProvider<?>> Builder provideCapabilities(Predicate<ModelNode> predicate, Class<E> providerClass) {
            return this.provideCapabilities(EnumSet.allOf(providerClass));
        }

        public Builder provideCapabilities(Set<? extends RuntimeCapabilityProvider<?>> providers) {
            return this.provideCapabilities(ModelNode::isDefined, providers);
        }

        public Builder provideCapabilities(Predicate<ModelNode> predicate, Set<? extends RuntimeCapabilityProvider<?>> providers) {
            this.capabilities = this.collectCapabilities(predicate, providers.stream().map(RuntimeCapabilityProvider::getCapability));
            return this;
        }

        public Builder withCapability(RuntimeCapability<?> capability) {
            return this.withCapability(ModelNode::isDefined, capability);
        }

        public Builder withCapability(Predicate<ModelNode> predicate, RuntimeCapability<?> capability) {
            this.capabilities = this.capabilities.isEmpty() ? Map.of(capability, predicate) : collectCapabilities(predicate, Stream.of(capability));
            return this;
        }

        public Builder withCapabilities(RuntimeCapability<?>... capabilities) {
            return this.withCapabilities(ModelNode::isDefined, capabilities);
        }

        public Builder withCapabilities(Predicate<ModelNode> predicate, RuntimeCapability<?>... capabilities) {
            return this.withCapabilities(List.of(capabilities));
        }

        public Builder withCapabilities(Collection<RuntimeCapability<?>> capabilities) {
            return this.withCapabilities(ModelNode::isDefined, capabilities);
        }

        public Builder withCapabilities(Predicate<ModelNode> predicate, Collection<RuntimeCapability<?>> capabilities) {
            this.capabilities = this.collectCapabilities(predicate, capabilities.stream());
            return this;
        }

        private Map<RuntimeCapability<?>, Predicate<ModelNode>> collectCapabilities(Predicate<ModelNode> predicate, Stream<RuntimeCapability<?>> capabilities) {
            Stream<Map.Entry<RuntimeCapability<?>, Predicate<ModelNode>>> stream = capabilities.map(capability -> Map.entry(capability, predicate));
            return (this.capabilities.isEmpty() ? stream : Stream.concat(this.capabilities.entrySet().stream(), stream)).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        public <E extends Enum<E> & PathElementProvider> Builder provideRequiredChildren(Class<E> providerClass) {
            return this.provideRequiredChildren(EnumSet.allOf(providerClass));
        }

        public Builder provideRequiredChildren(Set<? extends PathElementProvider> providers) {
            this.requiredChildren = this.collectRequiredChildren(providers.stream().map(PathElementProvider::getPathElement));
            return this;
        }

        public Builder requireChild(PathElement path) {
            return this.addRequiredChildren(Set.of(path));
        }

        public Builder requireChildren(PathElement... paths) {
            return this.addRequiredChildren(Set.of(paths));
        }

        public Builder requireChildren(Set<PathElement> paths) {
            return this.addRequiredChildren(Collections.unmodifiableSet(paths));
        }

        private Builder addRequiredChildren(Set<PathElement> paths) {
            this.requiredChildren = this.requiredChildren.isEmpty() ? paths : this.collectRequiredChildren(paths.stream());
            return this;
        }

        private Set<PathElement> collectRequiredChildren(Stream<PathElement> paths) {
            return (this.requiredChildren.isEmpty() ? paths : Stream.concat(this.requiredChildren.stream(), paths)).collect(Collectors.toUnmodifiableSet());
        }

        public <E extends Enum<E> & PathElementProvider> Builder provideRequiredSingletonChildren(Class<E> providerClass) {
            return this.provideRequiredSingletonChildren(EnumSet.allOf(providerClass));
        }

        public Builder provideRequiredSingletonChildren(Set<? extends PathElementProvider> providers) {
            this.requiredSingletonChildren = this.collectRequiredSingletonChildren(providers.stream().map(PathElementProvider::getPathElement));
            return this;
        }

        public Builder requireSingletonChild(PathElement path) {
            return this.addRequiredSingletonChildren(Set.of(path));
        }

        public Builder requireSingletonChildren(PathElement... paths) {
            return this.addRequiredSingletonChildren(Set.of(paths));
        }

        public Builder requireSingletonChildren(Set<PathElement> paths) {
            return this.addRequiredSingletonChildren(Collections.unmodifiableSet(paths));
        }

        public Builder addRequiredSingletonChildren(Set<PathElement> paths) {
            this.requiredSingletonChildren = this.requiredSingletonChildren.isEmpty() ? paths : this.collectRequiredChildren(paths.stream());
            return this;
        }

        private Set<PathElement> collectRequiredSingletonChildren(Stream<PathElement> paths) {
            return (this.requiredSingletonChildren.isEmpty() ? paths : Stream.concat(this.requiredSingletonChildren.stream(), paths)).collect(Collectors.toUnmodifiableSet());
        }

        public Builder withRuntimeResourceRegistrar(RuntimeResourceRegistrar registrar) {
            return this.withRuntimeResourceRegistrars(List.of(registrar));
        }

        public Builder withRuntimeResourceRegistrars(Collection<RuntimeResourceRegistrar> registrars) {
            return this.addRuntimeResourceRegistrars(Collections.unmodifiableCollection(registrars));
        }

        private Builder addRuntimeResourceRegistrars(Collection<RuntimeResourceRegistrar> registrars) {
            this.runtimeResourceRegistrars = this.runtimeResourceRegistrars.isEmpty() ? registrars : Stream.concat(this.runtimeResourceRegistrars.stream(), registrars.stream()).collect(Collectors.toUnmodifiableList());
            return this;
        }

        public Builder withResourceCapabilityReference(ResourceCapabilityReferenceRecorder<?> reference) {
            return this.addResourceCapabilityReferences(Set.of(reference));
        }

        public Builder withResourceCapabilityReferences(Set<ResourceCapabilityReferenceRecorder<?>> references) {
            return this.addResourceCapabilityReferences(Collections.unmodifiableSet(references));
        }

        private Builder addResourceCapabilityReferences(Set<ResourceCapabilityReferenceRecorder<?>> references) {
            this.resourceCapabilityReferences = this.resourceCapabilityReferences.isEmpty() ? references : Stream.concat(this.resourceCapabilityReferences.stream(), references.stream()).collect(Collectors.toUnmodifiableSet());
            return this;
        }

        public Builder withAddResourceOperationTransformation(UnaryOperator<OperationStepHandler> transformation) {
            this.addOperationTransformer = transformation;
            return this;
        }

        public Builder withOperationTransformation(UnaryOperator<OperationStepHandler> transformation) {
            this.operationTransformer = transformation;
            return this;
        }

        public Builder withResourceTransformation(UnaryOperator<Resource> transformation) {
            this.resourceTransformer = transformation;
            return this;
        }

        public Builder withDeploymentChainContributor(Consumer<DeploymentProcessorTarget> contributor) {
            this.deploymentChainContributor = Optional.of(contributor);
            return this;
        }

        public ResourceDescriptor build() {
            return new DefaultResourceDescriptor(this);
        }
    }
}
