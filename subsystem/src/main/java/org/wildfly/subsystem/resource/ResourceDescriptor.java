/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.wildfly.common.iteration.CompositeIterable;
import org.wildfly.subsystem.resource.capability.ResourceCapabilityReferenceRecorder;
import org.wildfly.subsystem.resource.operation.AddResourceOperationStepHandlerDescriptor;
import org.wildfly.subsystem.resource.operation.OperationStepHandlerDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.resource.operation.WriteAttributeOperationStepHandler;

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
        return new DefaultBuilder(resolver);
    }

    /**
     * Convenience method that exposes a collection of Suppliers as a stream of their supplied values.
     * @param <T> the supplied value type
     * @param <P> the provider type
     * @return a stream of attribute definitions.
     */
    static <T, P extends Supplier<T>> Stream<T> stream(Collection<P> providers) {
        return providers.stream().map(Supplier::get);
    }

    /**
     * The description resolver for the operation.
     * @return a description resolver
     */
    ResourceDescriptionResolver getResourceDescriptionResolver();

    /**
     * Returns a mapping of capability references to an ancestor resource.
     * @return a tuple of capability references and requirement resolvers.
     */
    default Set<ResourceCapabilityReferenceRecorder<?>> getResourceCapabilityReferences() {
        return Collections.emptySet();
    }

    /**
     * Returns a transformer to be applied to all operations that operate on an existing resource.
     * This is typically used to adapt legacy operations to conform to the current version of the model.
     * @return an operation handler transformer.
     * @deprecated Superseded by {@link #getOperationTransformation(String)}.
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    default UnaryOperator<OperationStepHandler> getResourceOperationTransformation() {
        return this.getOperationTransformation(""); // Returns default operation transformer
    }

    /**
     * Returns a transformer to be applied the specified operation.
     * This is typically used to adapt legacy operations to conform to the current version of the model.
     * @return an operation handler transformer.
     */
    default UnaryOperator<OperationStepHandler> getOperationTransformation(String operationName) {
        return UnaryOperator.identity();
    }

    /**
     * Attributes of the the resource affecting runtime.
     * @return a collection of attributes
     */
    default Iterable<AttributeDefinition> getAttributes() {
        return List.of();
    }

    /**
     * Returns custom operation handler for the specified attribute.
     * @return a {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#WRITE_ATTRIBUTE_OPERATION} operation handler
     */
    default OperationStepHandler getWriteAttributeOperationStepHandler(AttributeDefinition attribute) {
        return null;
    }

    /**
     * The capabilities provided by this resource
     * @return a set of capabilities
     */
    default Set<RuntimeCapability<?>> getCapabilities() {
        return Set.of();
    }

    /**
     * Returns a transformer for the add operation handler.
     * This is typically used to adapt legacy operations to conform to the current version of the model.
     * @return an operation handler transformer.
     * @deprecated Superseded by {@link #getOperationTransformation(String)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    default UnaryOperator<OperationStepHandler> getAddOperationTransformation() {
        return this.getOperationTransformation(ModelDescriptionConstants.ADD);
    }

    /**
     * Returns the restart flag for the {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#ADD}} operation of this resource.
     * @return an operation flag
     */
    default OperationEntry.Flag getAddOperationRestartFlag() {
        return OperationEntry.Flag.RESTART_NONE;
    }

    /**
     * Returns the restart flag for the {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#REMOVE} operation of this resource.
     * @return an operation flag
     */
    default OperationEntry.Flag getRemoveOperationRestartFlag() {
        return OperationEntry.Flag.RESTART_NONE;
    }

    /**
     * Default {@link ResourceDescriptor} implementation.
     */
    class DefaultResourceDescriptor implements ResourceDescriptor {
        static final BiPredicate<OperationContext, Resource> DISABLED_CAPABILITY = (context, resource) -> false;

        private final ResourceDescriptionResolver descriptionResolver;
        private final Optional<ResourceOperationRuntimeHandler> runtimeHandler;
        private final Map<RuntimeCapability<?>, BiPredicate<OperationContext, Resource>> capabilities;
        private final Map<AttributeDefinition, OperationStepHandler> readWriteAttributes = new HashMap<>();
        private final Iterable<? extends AttributeDefinition> readOnlyAttributes;
        private final Map<PathElement, ResourceRegistration> requiredChildren;
        private final Map<PathElement, ResourceRegistration> requiredSingletonChildren;
        private final Map<AttributeDefinition, AttributeTranslation> attributeTranslations;
        private final Set<ResourceCapabilityReferenceRecorder<?>> resourceCapabilityReferences;
        private final Map<String, UnaryOperator<OperationStepHandler>> operationTransformers;
        private final UnaryOperator<OperationStepHandler> defaultOperationTransformer;
        private final UnaryOperator<Resource> resourceTransformer;
        private final Optional<Consumer<DeploymentProcessorTarget>> deploymentChainContributor;
        private final OperationEntry.Flag addOperationRestartFlag;
        private final OperationEntry.Flag removeOperationRestartFlag;

        DefaultResourceDescriptor(AbstractConfigurator<?> builder) {
            this.descriptionResolver = builder.descriptionResolver;
            this.runtimeHandler = builder.runtimeHandler;
            this.capabilities = builder.capabilities;
            Collection<? extends AttributeDefinition> attributes = builder.attributes;
            if (!attributes.isEmpty()) {
                OperationStepHandler handler = new WriteAttributeOperationStepHandler(this);
                for (AttributeDefinition attribute : attributes) {
                    this.readWriteAttributes.put(attribute, handler);
                }
            }
            Collection<? extends AttributeDefinition> modelOnlyAttributes = builder.modelOnlyAttributes;
            if (!modelOnlyAttributes.isEmpty()) {
                OperationStepHandlerDescriptor descriptor = new OperationStepHandlerDescriptor() {
                    @Override
                    public BiPredicate<OperationContext, Resource> getCapabilityFilter(RuntimeCapability<?> capability) {
                        return DefaultResourceDescriptor.this.getCapabilityFilter(capability);
                    }
                };
                OperationStepHandler handler = new WriteAttributeOperationStepHandler(descriptor);
                for (AttributeDefinition attribute : modelOnlyAttributes) {
                    this.readWriteAttributes.put(attribute, handler);
                }
            }
            this.readWriteAttributes.putAll(builder.customAttributes);
            this.readOnlyAttributes = builder.readOnlyAttributes;
            this.attributeTranslations = builder.attributeTranslations;
            this.requiredChildren = builder.requiredChildren;
            this.requiredSingletonChildren = builder.requiredSingletonChildren;
            this.resourceCapabilityReferences = builder.resourceCapabilityReferences;
            this.operationTransformers = builder.operationTransformers;
            this.defaultOperationTransformer = builder.defaultOperationTransformer;
            this.resourceTransformer = builder.resourceTransformer;
            this.deploymentChainContributor = builder.deploymentChainContributor;
            this.addOperationRestartFlag = builder.addOperationRestartFlag;
            this.removeOperationRestartFlag = builder.removeOperationRestartFlag;
        }

        @Override
        public ResourceDescriptionResolver getResourceDescriptionResolver() {
            return this.descriptionResolver;
        }

        @Override
        public Optional<ResourceOperationRuntimeHandler> getRuntimeHandler() {
            return this.runtimeHandler;
        }

        @Override
        public Set<RuntimeCapability<?>> getCapabilities() {
            return this.capabilities.keySet();
        }

        @Override
        public BiPredicate<OperationContext, Resource> getCapabilityFilter(RuntimeCapability<?> capability) {
            return this.capabilities.getOrDefault(capability, DISABLED_CAPABILITY);
        }

        @Override
        public Set<ResourceCapabilityReferenceRecorder<?>> getResourceCapabilityReferences() {
            return this.resourceCapabilityReferences;
        }

        @Override
        public UnaryOperator<OperationStepHandler> getOperationTransformation(String operationName) {
            return this.operationTransformers.getOrDefault(operationName, this.defaultOperationTransformer);
        }

        @Override
        public Iterable<AttributeDefinition> getAttributes() {
            return new CompositeIterable<>(this.attributeTranslations.keySet(), this.readWriteAttributes.keySet(), this.readOnlyAttributes);
        }

        @Override
        public AttributeTranslation getAttributeTranslation(AttributeDefinition attribute) {
            return this.attributeTranslations.get(attribute);
        }

        @Override
        public OperationStepHandler getWriteAttributeOperationStepHandler(AttributeDefinition attribute) {
            return this.readWriteAttributes.get(attribute);
        }

        @Override
        public Map<PathElement, ResourceRegistration> getRequiredChildResources() {
            return this.requiredChildren;
        }

        @Override
        public Map<PathElement, ResourceRegistration> getRequiredSingletonChildResources() {
            return this.requiredSingletonChildren;
        }

        @Override
        public UnaryOperator<Resource> getResourceTransformation() {
            return this.resourceTransformer;
        }

        @Override
        public Optional<Consumer<DeploymentProcessorTarget>> getDeploymentChainContributor() {
            return this.deploymentChainContributor;
        }

        @Override
        public OperationEntry.Flag getAddOperationRestartFlag() {
            return this.addOperationRestartFlag;
        }

        @Override
        public OperationEntry.Flag getRemoveOperationRestartFlag() {
            return this.removeOperationRestartFlag;
        }
    }

    /**
     * Configures the characteristics of a {@link ResourceDescriptor}.
     * @param <C> the configurator type
     */
    static interface Configurator<C extends Configurator<C>> {
        /**
         * Applies the specified runtime handler to the operations of this resource.
         * @param runtimeHandler a runtime handler.
         * @return a reference to this configurator
         */
        C withRuntimeHandler(ResourceOperationRuntimeHandler runtimeHandler);

        /**
         * Overrides the default restart flag for the {@value ModelDescriptionConstants#ADD} operation of this resource.
         * @param restartFlag a restart flag
         * @return a reference to this configurator
         */
        C withAddOperationRestartFlag(OperationEntry.Flag restartFlag);

        /**
         * Overrides the default restart flag for the {@value ModelDescriptionConstants#REMOVE} operation of this resource.
         * @param restartFlag a restart flag
         * @return a reference to this configurator
         */
        C withRemoveOperationRestartFlag(OperationEntry.Flag restartFlag);

        /**
         * Adds the specified attributes to this resource descriptor.
         * @param attributes a collection of attributes
         * @return a reference to this configurator
         */
        C addAttributes(Collection<AttributeDefinition> attributes);

        /**
         * Adds the attribute with the specified custom {@value ModelDescriptionConstants#WRITE_ATTRIBUTE_OPERATION} operation handler.
         * @param attribute an attribute
         * @param writeAttributeHandler custom {@value ModelDescriptionConstants#WRITE_ATTRIBUTE_OPERATION} operation handler
         * @return a reference to this configurator
         */
        C addAttribute(AttributeDefinition attribute, OperationStepHandler writeAttributeHandler);

        /**
         * Adds the specified model-only attributes (i.e. with no runtime handling) to this resource descriptor.
         * @param attributes a collection of attributes
         * @return a reference to this configurator
         */
        C addModelOnlyAttributes(Collection<AttributeDefinition> attributes);

        /**
         * Adds the specified read-only attributes to this resource descriptor.
         * @param attributes a collection of attributes
         * @return a reference to this configurator
         */
        C addReadOnlyAttributes(Collection<AttributeDefinition> attributes);

        /**
         * Specifies an attribute alias to another attribute of this resource.
         * @param attribute the alias attribute
         * @param targetAttribute the target attribute
         * @return a reference to this configurator
         */
        default C renameAttribute(AttributeDefinition attribute, AttributeDefinition targetAttribute) {
            return this.translateAttribute(attribute, AttributeTranslation.alias(targetAttribute));
        }

        /**
         * Specifies an attribute alias to another attribute of a potentially different resource.
         * @param attribute the alias attribute
         * @param translation a description of the attribute translation
         * @return a reference to this configurator
         */
        C translateAttribute(AttributeDefinition attribute, AttributeTranslation translation);

        /**
         * Adds the specified runtime capability to this resource.
         * @param capability a runtime capability
         * @return a reference to this configurator
         */
        default C addCapability(RuntimeCapability<?> capability) {
            return this.addCapability(capability, AbstractConfigurator.DEFAULT_CAPABILITY_FILTER);
        }

        /**
         * Adds the specified conditionally-registered runtime capability to this resource
         * @param capability a runtime capability
         * @param filter a predicate used to determine when the specified capability should be registered
         * @return a reference to this configurator
         */
        C addCapability(RuntimeCapability<?> capability, BiPredicate<OperationContext, Resource> filter);

        /**
         * Adds the specified runtime capabilities to this resource.
         * @param capabilities a collection of runtime capabilities
         * @return a reference to this configurator
         */
        default C addCapabilities(Collection<RuntimeCapability<?>> capabilities) {
            return this.addCapabilities(capabilities, AbstractConfigurator.DEFAULT_CAPABILITY_FILTER);
        }

        /**
         * Adds the specified runtime capabilities to this resource.
         * @param capabilities a collection of runtime capabilities
         * @param filter a predicate used to determine when the specified capability should be registered
         * @return a reference to this configurator
         */
        C addCapabilities(Collection<RuntimeCapability<?>> capabilities, BiPredicate<OperationContext, Resource> filter);

        /**
         * Defines a required child of this resource.  Required children will be automatically added, if no child resource exists with the specified path.
         * @param path the path of the required child resource
         * @return a reference to this configurator
         * @deprecated Superseded by {@link #requireChildResource(ResourceRegistration)}.
         */
        @Deprecated(forRemoval = true, since = "26.0.0")
        default C requireChild(PathElement path) {
            return this.requireChildResources(Set.of(ResourceRegistration.of(path)));
        }

        /**
         * Defines a set of required children of this resource.  Required children will be automatically added, if no child resource exists with the specified path.
         * @param paths a set of paths of the required child resources
         * @return a reference to this configurator
         * @deprecated Superseded by {@link #requireChildResources(Set)}.
         */
        @Deprecated(forRemoval = true, since = "26.0.0")
        default C requireChildren(Set<PathElement> paths) {
            return this.requireChildResources(paths.stream().map(ResourceRegistration::of).collect(Collectors.toUnmodifiableSet()));
        }

        /**
         * Defines a required child of this resource.  Required children will be automatically added, if no child resource exists with the specified path.
         * @param path the path of the required child resource
         * @return a reference to this configurator
         */
        default C requireChildResource(ResourceRegistration child) {
            return this.requireChildResources(Set.of(child));
        }

        /**
         * Defines a set of required children of this resource.  Required children will be automatically added, if no child resource exists with the specified path.
         * @param paths a set of paths of the required child resources
         * @return a reference to this configurator
         */
        C requireChildResources(Set<? extends ResourceRegistration> children);

        /**
         * Defines a required singleton child of this resource.  Required singleton children will be automatically added, if no child resource exists with the same path key.
         * @param path the path of the required singleton child resource
         * @return a reference to this configurator
         * @deprecated Superseded by {@link #requireSingletonChildResource(ResourceRegistration)}.
         */
        @Deprecated(forRemoval = true, since = "26.0.0")
        default C requireSingletonChild(PathElement path) {
            return this.requireSingletonChildResources(Set.of(ResourceRegistration.of(path)));
        }

        /**
         * Defines a set of required singleton children of this resource.  Required singleton children will be automatically added, if no child resource exists with the same path key.
         * @param paths a set of paths of the required singleton child resources
         * @return a reference to this configurator
         * @deprecated Superseded by {@link #requireSingletonChildResources(Set)}.
         */
        @Deprecated(forRemoval = true, since = "26.0.0")
        default C requireSingletonChild(Set<PathElement> paths) {
            return this.requireSingletonChildResources(paths.stream().map(ResourceRegistration::of).collect(Collectors.toUnmodifiableSet()));
        }

        /**
         * Defines a required singleton child of this resource.  Required singleton children will be automatically added, if no child resource exists with the same path key.
         * @param path the path of the required singleton child resource
         * @return a reference to this configurator
         */
        default C requireSingletonChildResource(ResourceRegistration path) {
            return this.requireSingletonChildResources(Set.of(path));
        }

        /**
         * Defines a set of required singleton children of this resource.  Required singleton children will be automatically added, if no child resource exists with the same path key.
         * @param paths a set of paths of the required singleton child resources
         * @return a reference to this configurator
         */
        C requireSingletonChildResources(Set<? extends ResourceRegistration> paths);

        /**
         * Adds a capability reference that records a requirement for this resource.
         * @param reference a capability reference recorder
         * @return a reference to this configurator
         */
        default C addResourceCapabilityReference(ResourceCapabilityReferenceRecorder<?> reference) {
            return this.addResourceCapabilityReferences(Set.of(reference));
        }

        /**
         * Adds a number of capability references that records requirements for this resource.
         * @param references a collection of capability reference recorders
         * @return a reference to this configurator
         */
        C addResourceCapabilityReferences(Collection<ResourceCapabilityReferenceRecorder<?>> references);

        /**
         * Applies the specified transformation to the {@value ModelDescriptionConstants#ADD} operation of this resource.
         * @param transformation an operation handler transformation
         * @return a reference to this configurator
         * @deprecated Superseded by {@link #withOperationTransformation(String, UnaryOperator)}.
         */
        @Deprecated(forRemoval = true, since = "26.0.0")
        default C withAddResourceOperationTransformation(UnaryOperator<OperationStepHandler> transformation) {
            return this.withOperationTransformation(ModelDescriptionConstants.ADD, transformation);
        }

        /**
         * Applies the specified transformation to all operations for this resource.
         * @param transformation an operation handler transformation
         * @return a reference to this configurator
         */
        C withOperationTransformation(UnaryOperator<OperationStepHandler> transformation);

        /**
         * Applies the specified transformation to the specified operation for this resource.
         * @param transformation an operation handler transformation
         * @return a reference to this configurator
         */
        default C withOperationTransformation(String operationName, UnaryOperator<OperationStepHandler> transformation) {
            return this.withOperationTransformation(Set.of(operationName), transformation);
        }

        /**
         * Applies the specified transformation to the specified operations for this resource.
         * @param transformation an operation handler transformation
         * @return a reference to this configurator
         */
        C withOperationTransformation(Set<String> operationNames, UnaryOperator<OperationStepHandler> transformation);

        /**
         * Applies the specified transformation to the {@link Resource} created by this resource's {@value ModelDescriptionConstants#ADD} operation.
         * @param transformation an operation handler transformation
         * @return a reference to this configurator
         */
        C withResourceTransformation(UnaryOperator<Resource> transformation);

        /**
         * Applies the specified deployment chain contributor to this resource's {@value ModelDescriptionConstants#ADD} operation.
         * @param contributor a deployment chain contribution
         * @return a reference to this configurator
         */
        C withDeploymentChainContributor(Consumer<DeploymentProcessorTarget> contributor);

        /**
         * Adds the specified attribute providers to this resource descriptor.
         * @param providers a collection of attribute providers
         * @return a reference to this configurator
         */
        <P extends Supplier<AttributeDefinition>> C provideAttributes(Collection<P> providers);

        /**
         * Adds the specified model-only attribute providers to this resource descriptor.
         * @param providers a collection of attribute providers
         * @return a reference to this configurator
         */
        <P extends Supplier<AttributeDefinition>> C provideModelOnlyAttributes(Collection<P> providers);

        /**
         * Adds the specified read-only attribute providers to this resource descriptor.
         * @param providers a collection of attribute providers
         * @return a reference to this configurator
         */
        <P extends Supplier<AttributeDefinition>> C provideReadOnlyAttributes(Collection<P> providers);

        /**
         * Adds the specified runtime capability providers to this resource descriptor.
         * @param providers a collection of runtime capability providers
         * @return a reference to this configurator
         */
        default <P extends Supplier<RuntimeCapability<?>>> C provideCapabilities(Collection<P> providers) {
            return this.provideCapabilities(providers, AbstractConfigurator.DEFAULT_CAPABILITY_FILTER);
        }

        /**
         * Adds the specified conditionally-registered runtime capability providers to this resource descriptor.
         * @param providers a collection of runtime capability providers
         * @param filter a predicate used to determine when the specified capability should be registered
         * @return a reference to this configurator
         */
        <P extends Supplier<RuntimeCapability<?>>> C provideCapabilities(Collection<P> providers, BiPredicate<OperationContext, Resource> filter);

        /**
         * Defines a set of required children of this resource.  Required children will be automatically added, if no child resource exists with the specified path.
         * @param providers a set of providers of the required child resource paths
         * @return a reference to this configurator
         * @deprecated Use {@link #requireChildResources(Set)} instead.
         */
        @Deprecated(forRemoval = true, since = "26.0.0")
        default <P extends Supplier<PathElement>> C provideRequiredChildren(Collection<P> providers) {
            return this.requireChildResources(providers.stream().map(Supplier::get).map(ResourceRegistration::of).collect(Collectors.toUnmodifiableSet()));
        }

        /**
         * Defines a set of required singleton children of this resource.  Required singleton children will be automatically added, if no child resource exists with the same path key.
         * @param providers a set of providers of the required singleton child resource paths
         * @return a reference to this configurator
         * @deprecated Use {@link #requireSingletonChildResources(Set)} instead.
         */
        @Deprecated(forRemoval = true, since = "26.0.0")
        default <P extends Supplier<PathElement>> C provideRequiredSingletonChildren(Collection<P> providers) {
            return this.requireSingletonChildResources(providers.stream().map(Supplier::get).map(ResourceRegistration::of).collect(Collectors.toUnmodifiableSet()));
        }
    }

    /**
     * Builds a {@link ResourceDescriptor}.
     */
    interface Builder extends Configurator<Builder> {
        /**
         * Builds a resource descriptor.
         * @return a resource descriptor.
         */
        ResourceDescriptor build();
    }

    /**
     * An abstract {@link ResourceDescriptor} configurator.
     */
    abstract static class AbstractConfigurator<C extends Configurator<C>> implements Configurator<C> {
        static final BiPredicate<OperationContext, Resource> DEFAULT_CAPABILITY_FILTER = (context, resource) -> resource.getModel().isDefined();

        private static final Set<OperationEntry.Flag> RESTART_FLAGS = EnumSet.of(OperationEntry.Flag.RESTART_ALL_SERVICES, OperationEntry.Flag.RESTART_JVM, OperationEntry.Flag.RESTART_NONE, OperationEntry.Flag.RESTART_RESOURCE_SERVICES);
        private static final Set<OperationEntry.Flag> RELOAD_RESTART_FLAGS = EnumSet.of(OperationEntry.Flag.RESTART_ALL_SERVICES, OperationEntry.Flag.RESTART_JVM);

        private final ResourceDescriptionResolver descriptionResolver;
        private Optional<ResourceOperationRuntimeHandler> runtimeHandler = Optional.empty();
        private OperationEntry.Flag addOperationRestartFlag = OperationEntry.Flag.RESTART_NONE;
        private OperationEntry.Flag removeOperationRestartFlag = OperationEntry.Flag.RESTART_NONE;
        private Map<RuntimeCapability<?>, BiPredicate<OperationContext, Resource>> capabilities = Map.of();
        private Collection<AttributeDefinition> attributes = List.of();
        private Collection<AttributeDefinition> modelOnlyAttributes = List.of();
        private Collection<AttributeDefinition> readOnlyAttributes = List.of();
        private Map<AttributeDefinition, OperationStepHandler> customAttributes = Map.of();
        private Map<PathElement, ResourceRegistration> requiredChildren = Map.of();
        private Map<PathElement, ResourceRegistration> requiredSingletonChildren = Map.of();
        private Map<AttributeDefinition, AttributeTranslation> attributeTranslations = Map.of();
        private Set<ResourceCapabilityReferenceRecorder<?>> resourceCapabilityReferences = Set.of();
        private Map<String, UnaryOperator<OperationStepHandler>> operationTransformers = Map.of();
        private UnaryOperator<OperationStepHandler> defaultOperationTransformer = UnaryOperator.identity();
        private UnaryOperator<Resource> resourceTransformer = UnaryOperator.identity();
        private Optional<Consumer<DeploymentProcessorTarget>> deploymentChainContributor = Optional.empty();

        AbstractConfigurator(ResourceDescriptionResolver descriptionResolver) {
            this.descriptionResolver = descriptionResolver;
        }

        protected abstract C self();

        @Override
        public C withRuntimeHandler(ResourceOperationRuntimeHandler runtimeHandler) {
            this.runtimeHandler = Optional.of(runtimeHandler);
            if (this.removeOperationRestartFlag == OperationEntry.Flag.RESTART_NONE) {
                this.removeOperationRestartFlag = OperationEntry.Flag.RESTART_RESOURCE_SERVICES;
            }
            return this.self();
        }

        @Override
        public C withAddOperationRestartFlag(OperationEntry.Flag restartFlag) {
            if (!RESTART_FLAGS.contains(restartFlag)) {
                throw new IllegalArgumentException(restartFlag.name());
            }
            this.addOperationRestartFlag = restartFlag;
            return this.self();
        }

        @Override
        public C withRemoveOperationRestartFlag(OperationEntry.Flag restartFlag) {
            if (!RESTART_FLAGS.contains(restartFlag)) {
                throw new IllegalArgumentException(restartFlag.name());
            }
            this.removeOperationRestartFlag = restartFlag;
            return this.self();
        }

        @Override
        public C addAttributes(Collection<AttributeDefinition> attributes) {
            this.attributes = this.attributes.isEmpty() ? copyOf(attributes) : concat(this.attributes, attributes.stream());
            return this.self();
        }

        @Override
        public C addAttribute(AttributeDefinition attribute, OperationStepHandler writeAttributeHandler) {
            this.customAttributes = concat(this.customAttributes, attribute, writeAttributeHandler);
            return this.self();
        }

        @Override
        public C addModelOnlyAttributes(Collection<AttributeDefinition> attributes) {
            this.modelOnlyAttributes = this.modelOnlyAttributes.isEmpty() ? copyOf(attributes) : concat(this.modelOnlyAttributes, attributes.stream());
            return this.self();
        }

        @Override
        public C addReadOnlyAttributes(Collection<AttributeDefinition> attributes) {
            this.readOnlyAttributes = this.readOnlyAttributes.isEmpty() ? copyOf(attributes) : concat(this.readOnlyAttributes, attributes.stream());
            return this.self();
        }

        @Override
        public C translateAttribute(AttributeDefinition attribute, AttributeTranslation translation) {
            assert attribute.getFlags().contains(AttributeAccess.Flag.ALIAS);
            this.attributeTranslations = concat(this.attributeTranslations, attribute, translation);
            return this.self();
        }

        @Override
        public C addCapability(RuntimeCapability<?> capability, BiPredicate<OperationContext, Resource> filter) {
            this.capabilities = concat(this.capabilities, capability, filter);
            return this.self();
        }

        @Override
        public C addCapabilities(Collection<RuntimeCapability<?>> capabilities, BiPredicate<OperationContext, Resource> filter) {
            this.capabilities = concat(this.capabilities, capabilities.stream(), filter);
            return this.self();
        }

        @Override
        public C requireChildResources(Set<? extends ResourceRegistration> children) {
            this.requiredChildren = this.requiredChildren.isEmpty() ? children.stream().collect(Collectors.toMap(ResourceRegistration::getPathElement, Function.identity())) : concat(this.requiredChildren, children.stream().map(child -> Map.entry(child.getPathElement(), child)));
            return this.self();
        }

        @Override
        public C requireSingletonChildResources(Set<? extends ResourceRegistration> children) {
            this.requiredSingletonChildren = this.requiredSingletonChildren.isEmpty() ? children.stream().collect(Collectors.toMap(ResourceRegistration::getPathElement, Function.identity())) : concat(this.requiredSingletonChildren, children.stream().map(child -> Map.entry(child.getPathElement(), child)));
            return this.self();
        }

        @Override
        public C addResourceCapabilityReferences(Collection<ResourceCapabilityReferenceRecorder<?>> references) {
            this.resourceCapabilityReferences = references.isEmpty() ? Set.copyOf(references) : concat(this.resourceCapabilityReferences, references.stream());
            return this.self();
        }

        @Override
        public C withOperationTransformation(UnaryOperator<OperationStepHandler> transformation) {
            this.defaultOperationTransformer = transformation;
            return this.self();
        }

        @Override
        public C withOperationTransformation(Set<String> operationNames, UnaryOperator<OperationStepHandler> transformation) {
            this.operationTransformers = concat(this.operationTransformers, operationNames.stream(), transformation);
            return this.self();
        }

        @Override
        public C withResourceTransformation(UnaryOperator<Resource> transformation) {
            this.resourceTransformer = transformation;
            return this.self();
        }

        @Override
        public C withDeploymentChainContributor(Consumer<DeploymentProcessorTarget> contributor) {
            this.deploymentChainContributor = Optional.of(contributor);
            // If this resource contributes to the deployment chain, adding or removing it requires a reload
            if (!RELOAD_RESTART_FLAGS.contains(this.addOperationRestartFlag)) {
                this.addOperationRestartFlag = OperationEntry.Flag.RESTART_ALL_SERVICES;
            }
            if (!RELOAD_RESTART_FLAGS.contains(this.removeOperationRestartFlag)) {
                this.removeOperationRestartFlag = OperationEntry.Flag.RESTART_ALL_SERVICES;
            }
            return this.self();
        }

        @Override
        public <P extends Supplier<AttributeDefinition>> C provideAttributes(Collection<P> providers) {
            this.attributes = concat(this.attributes, stream(providers));
            return this.self();
        }

        @Override
        public <P extends Supplier<AttributeDefinition>> C provideModelOnlyAttributes(Collection<P> providers) {
            this.modelOnlyAttributes = concat(this.modelOnlyAttributes, stream(providers));
            return this.self();
        }

        @Override
        public <P extends Supplier<AttributeDefinition>> C provideReadOnlyAttributes(Collection<P> providers) {
            this.readOnlyAttributes = concat(this.readOnlyAttributes, stream(providers));
            return this.self();
        }

        @Override
        public <P extends Supplier<RuntimeCapability<?>>> C provideCapabilities(Collection<P> providers, BiPredicate<OperationContext, Resource> filter) {
            this.capabilities = concat(this.capabilities, stream(providers), filter);
            return this.self();
        }

        private static <T> Collection<T> copyOf(Collection<T> collection) {
            // Create defensive copy, if collection was not already immutable
            return (collection instanceof Set) ? Set.copyOf((Set<T>) collection) : List.copyOf(collection);
        }

        private static <T> Collection<T> concat(Collection<T> collection, Stream<? extends T> additions) {
            return (collection.isEmpty() ? additions : Stream.concat(collection.stream(), additions)).collect(Collectors.toUnmodifiableList());
        }

        private static <T> Set<T> concat(Set<T> set, Stream<? extends T> additions) {
            return (set.isEmpty() ? additions : Stream.concat(set.stream(), additions)).collect(Collectors.toUnmodifiableSet());
        }

        private static <K, V> Map<K, V> concat(Map<K, V> map, K key, V value) {
            return map.isEmpty() ? Map.of(key, value) : concat(map, Stream.of(Map.entry(key, value)));
        }

        private static <K, V> Map<K, V> concat(Map<K, V> map, Stream<K> keys, V value) {
            return concat(map, keys.map(key -> Map.entry(key, value)));
        }

        private static <K, V> Map<K, V> concat(Map<K, V> map, Stream<Map.Entry<K, V>> entries) {
            return (map.isEmpty() ? entries : Stream.concat(map.entrySet().stream(), entries)).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    /**
     * An default {@link ResourceDescriptor} builder.
     */
    static class DefaultBuilder extends AbstractConfigurator<Builder> implements Builder {

        DefaultBuilder(ResourceDescriptionResolver descriptionResolver) {
            super(descriptionResolver);
        }

        @Override
        public ResourceDescriptor build() {
            return new DefaultResourceDescriptor(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
