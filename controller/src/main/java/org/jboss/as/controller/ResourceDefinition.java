/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DefaultResourceDescriptionProvider;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;

/**
 * Provides essential information defining a management resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ResourceDefinition extends ResourceRegistration {

    /**
     * Gets a {@link DescriptionProvider} for the given resource.
     *
     * @param resourceRegistration  the resource. Cannot be {@code null}
     * @return  the description provider. Will not be {@code null}
     */
    DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration resourceRegistration);

    /**
     * Register operations associated with this resource.
     *
     * @param resourceRegistration a {@link ManagementResourceRegistration} created from this definition
     */
    void registerOperations(final ManagementResourceRegistration resourceRegistration);

    /**
     * Register operations associated with this resource.
     *
     * @param resourceRegistration a {@link ManagementResourceRegistration} created from this definition
     */
    void registerAttributes(final ManagementResourceRegistration resourceRegistration);

    /**
     * Register notifications associated with this resource.
     *
     * @param resourceRegistration a {@link ManagementResourceRegistration} created from this definition
     */
    void registerNotifications(final ManagementResourceRegistration resourceRegistration);

    /**
     * Register child resources associated with this resource.
     *
     * @param resourceRegistration a {@link ManagementResourceRegistration} created from this definition
     */
    void registerChildren(final ManagementResourceRegistration resourceRegistration);

    /**
     * Register capabilities associated with this resource.
     *
     * @param resourceRegistration a {@link ManagementResourceRegistration} created from this definition
     */
    default void registerCapabilities(final ManagementResourceRegistration resourceRegistration) {
        // no op
    }

    /**
     * Register "additional" Galleon packages that must be installed in order
     * for this Resource to function. NB: the packages need to be visible from the feature pack
     * that contains the ResourceDefinition. It can't be any package from any feature pack.
     * The purpose of providing this information is to
     * make it available to the Galleon tooling that produces Galleon feature-specs,
     * in order to allow the tooling to include the package information in the relevant
     * spec.
     * <p>
     * A package is "additional" if it is not one of the "standard" packages that must be
     * installed. The names of "standard" packages should not be registered. The "standard"
     * packages are:
     *
     *  <ol>
     *      <li>
     *          The root package for the process type; i.e. the package that provides
     *          the main module whose name is passed to JBoss Modules when the process
     *          is launched.
     *      </li>
     *      <li>
     *          The package that installs the module that provides the extension in
     *          which the resource is defined.
     *      </li>
     *      <li>
     *          Any package that is non-optionally directly or transitively required
     *          by one of the other types of standard packages.
     *      </li>
     *  </ol>
     * Additional packages fall into in the following categories:
     *  <ol>
     *      <li>
     *          Packages that install required modules injected into Deployment Unit can be registered as
     *          <i>required</i> {@link org.jboss.as.controller.registry.RuntimePackageDependency}.
     *      </li>
     *      <li>
     *          Packages that install optional modules injected into Deployment Unit can be registered as
     *          <i>optional</i> {@link org.jboss.as.controller.registry.RuntimePackageDependency}.
     *      </li>
     *      <li>
     *          Packages that install modules that are only required if the resource associated to this
     *          definition is instantiated are to be registered as <i>required</i>
     *          {@link org.jboss.as.controller.registry.RuntimePackageDependency}.
     *      </li>
     *      <li>
     *          Packages that install modules that are only required by this feature in order to interact with other features
     *          are to be registered as <i>passive</i>
     *          {@link org.jboss.as.controller.registry.RuntimePackageDependency}. A passive dependency is provisioned
     *          only if its own required dependencies are present.
     *      </li>
     *  </ol>
     * @param resourceRegistration a {@link ManagementResourceRegistration}
     * created from this definition
     */
    default void registerAdditionalRuntimePackages(final ManagementResourceRegistration resourceRegistration) {
        // no op
    }

    /**
     * Get the definition of any access constraints associated with the resource.
     *
     * @return the access constraints or an empty list; will not return {@code null}.
     */
    List<AccessConstraintDefinition> getAccessConstraints();

    /**
     *
     * @return true if resource is runtime
     * @since WildFly Core 1.0, WildFly 9.0
     */
    boolean isRuntime();

    /**
     * Whether this is an ordered child or not
     *
     * @return {@code true} if this child is ordered within the parent, false otherwise
     */
    boolean isOrderedChild();

    /**
     * Gets the maximum number of times a resource of the type described by this definition
     * can occur under its parent resource (or, for a root resource, the minimum number of times it can
     * occur at all.)
     *
     * @return the minimum number of occurrences
     */
    default int getMaxOccurs() {
        PathElement pe = getPathElement();
        return pe == null || !pe.isWildcard() ? 1 : Integer.MAX_VALUE;
    }

    /**
     * Gets the minimum number of times a resource of the type described by this definition
     * can occur under its parent resource (or, for a root resource, the number of times it can
     * occur at all.)
     *
     * @return the minimum number of occurrences
     */
    default int getMinOccurs() {
        return getPathElement() != null ? 0 : 1;
    }

    default boolean isFeature() {
        return false;
    }

    /**
     * Creates a minimal {@link ResourceDefinition} builder using the specified registration and description resolver.
     * @param registration the resource registration
     * @param descriptionResolver the resource description resolver
     * @return a builder instance
     */
    static Builder builder(ResourceRegistration registration, ResourceDescriptionResolver descriptionResolver) {
        return new MinimalBuilder(registration, descriptionResolver);
    }

    /**
     * Creates a minimal {@link ResourceDefinition} builder using the specified registration and description resolver, deprecated as of the version of the specified model.
     * @param registration the resource registration
     * @param descriptionResolver a resolver of model descriptions for this resource
     * @param deprecation the model that deprecates this resource
     * @return a builder instance
     */
    static Builder builder(ResourceRegistration registration, ResourceDescriptionResolver descriptionResolver, SubsystemModel deprecation) {
        return new MinimalBuilder(registration, descriptionResolver, new DeprecationData(deprecation.getVersion()));
    }

    /**
     * Creates a {@link ResourceDefinition} builder using the specified registration and description resolver
     * @param registration the resource registration
     * @param descriptionProvider provides model descriptions for this resource
     * @return a builder instance
     */
    static Builder builder(ResourceRegistration registration, DescriptionProvider descriptionProvider) {
        return new MinimalBuilder(registration, descriptionProvider);
    }

    /**
     * Configures the basic characteristics of a {@link ResourceDefinition}.
     * @param <C> the configurator type
     */
    interface Configurator<C extends Configurator<C>> {
        /**
         * Configures the resource with an additional access constraint.
         * @param accessConstraint an access constraint
         * @return a reference to this configurator
         */
        default C withAccessConstraint(AccessConstraintDefinition accessConstraint) {
            return this.withAccessConstraints(List.of(accessConstraint));
        }

        /**
         * Configures the resource with access constraints.
         * @param accessConstraints a variable number of access constraints
         * @return a reference to this configurator
         */
        default C withAccessConstraints(AccessConstraintDefinition... accessConstraints) {
            return this.withAccessConstraints(List.of(accessConstraints));
        }

        /**
         * Configures the resource with access constraints.
         * @param accessConstraints a collection of access constraints
         * @return a reference to this configurator
         */
        C withAccessConstraints(Collection<AccessConstraintDefinition> accessConstraints);

        /**
         * Configures the resource with an additional access constraint.
         * @param accessConstraint an access constraint
         * @return a reference to this configurator
         */
        default C addAccessConstraint(AccessConstraintDefinition accessConstraint) {
            return this.addAccessConstraints(List.of(accessConstraint));
        }

        /**
         * Configures the resource with additional access constraints.
         * @param accessConstraints a variable number of access constraints
         * @return a reference to this configurator
         */
        default C addAccessConstraints(AccessConstraintDefinition... accessConstraints) {
            return this.addAccessConstraints(List.of(accessConstraints));
        }

        /**
         * Configures the resource with additional access constraints.
         * @param accessConstraints a collection of access constraints
         * @return a reference to this configurator
         */
        C addAccessConstraints(Collection<AccessConstraintDefinition> accessConstraints);

        /**
         * Configures the resource as a runtime-only resource.
         * @return a reference to this configurator
         */
        C asRuntime();

        /**
         * Configures the resource as an ordered child resource of its parent.
         * @return a reference to this configurator
         */
        C asOrderedChild();

        /**
         * Configures the resource as non-feature with respect to galleon.
         * @return a reference to this configurator
         */
        C asNonFeature();

        /**
         * Configures the minimum cardinality of this resource
         * @return a reference to this configurator
         */
        C withMinOccurance(int min);

        /**
         * Configures the maximum cardinality of this resource
         * @return a reference to this configurator
         */
        C withMaxOccurance(int max);
    }

    /**
     * Configures the basic characteristics of a {@link ResourceDefinition}.
     */
    abstract class AbstractConfigurator<C extends Configurator<C>> implements Configurator<C> {
        private final PathElement path;
        private final Stability stability;
        private final Function<ImmutableManagementResourceRegistration, DescriptionProvider> descriptionProviderFactory;
        private int minOccurance;
        private int maxOccurance;
        private List<AccessConstraintDefinition> accessConstraints = List.of();
        private boolean runtime = false;
        private boolean orderedChild = false;
        private boolean feature = true;

        AbstractConfigurator(ResourceRegistration registration, Function<ImmutableManagementResourceRegistration, DescriptionProvider> descriptionProviderFactory) {
            this.path = registration.getPathElement();
            this.stability = registration.getStability();
            this.descriptionProviderFactory = descriptionProviderFactory;
            this.minOccurance = (path == null) ? 1 : 0;
            this.maxOccurance = (path == null) || !path.isWildcard() ? 1 : Integer.MAX_VALUE;
        }

        protected abstract C self();

        @Override
        public C withMinOccurance(int occurance) {
            this.minOccurance = occurance;
            return this.self();
        }

        @Override
        public C withMaxOccurance(int occurance) {
            this.maxOccurance = occurance;
            return this.self();
        }

        @Override
        public C withAccessConstraints(Collection<AccessConstraintDefinition> accessConstraints) {
            this.accessConstraints = List.copyOf(accessConstraints);
            return this.self();
        }

        @Override
        public C addAccessConstraints(Collection<AccessConstraintDefinition> accessConstraints) {
            if (this.accessConstraints.isEmpty()) {
                return this.withAccessConstraints(accessConstraints);
            }
            this.accessConstraints = Stream.concat(this.accessConstraints.stream(), accessConstraints.stream()).collect(Collectors.toUnmodifiableList());
            return this.self();
        }

        @Override
        public C asRuntime() {
            this.runtime = true;
            return this.self();
        }

        @Override
        public C asOrderedChild() {
            this.orderedChild = true;
            return this.self();
        }

        @Override
        public C asNonFeature() {
            this.feature = false;
            return this.self();
        }
    }

    /**
     * Builder of a minimal {@link ResourceDefinition}.
     */
    interface Builder extends Configurator<Builder> {
        /**
         * Builds a configured resource definition.
         * @return a resource definition.
         */
        ResourceDefinition build();
    }

    /**
     * Minimal builder of a {@link ResourceDefinition}.
     */
    static class MinimalBuilder extends AbstractConfigurator<Builder> implements Builder {

        MinimalBuilder(ResourceRegistration registration, ResourceDescriptionResolver resolver) {
            super(registration, new Function<>() {
                @Override
                public DescriptionProvider apply(ImmutableManagementResourceRegistration registration) {
                    return new DefaultResourceDescriptionProvider(registration, resolver);
                }
            });
        }

        MinimalBuilder(ResourceRegistration registration, ResourceDescriptionResolver resolver, DeprecationData deprecation) {
            super(registration, new Function<>() {
                @Override
                public DescriptionProvider apply(ImmutableManagementResourceRegistration registration) {
                    return new DefaultResourceDescriptionProvider(registration, resolver, deprecation);
                }
            });
        }

        MinimalBuilder(ResourceRegistration registration, DescriptionProvider provider) {
            super(registration, new Function<>() {
                @Override
                public DescriptionProvider apply(ImmutableManagementResourceRegistration registration) {
                    return provider;
                }
            });
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ResourceDefinition build() {
            return new MinimalResourceDefinition(this);
        }
    }

    /**
     * A minimal {@link ResourceDefinition} implementation whose internal registration will be performed separately.
     */
    static class MinimalResourceDefinition implements ResourceDefinition {

        private final PathElement path;
        private final Stability stability;
        private final Function<ImmutableManagementResourceRegistration, DescriptionProvider> descriptionProviderFactory;
        private final int minOccurance;
        private final int maxOccurance;
        private final List<AccessConstraintDefinition> accessConstraints;
        private final boolean runtime;
        private final boolean orderedChild;
        private final boolean feature;

        MinimalResourceDefinition(AbstractConfigurator<?> configurator) {
            this.path = configurator.path;
            this.stability = configurator.stability;
            this.descriptionProviderFactory = configurator.descriptionProviderFactory;
            this.minOccurance = configurator.minOccurance;
            this.maxOccurance = configurator.maxOccurance;
            this.accessConstraints = configurator.accessConstraints;
            this.runtime = configurator.runtime;
            this.orderedChild = configurator.orderedChild;
            this.feature = configurator.feature;
        }

        @Override
        public PathElement getPathElement() {
            return this.path;
        }

        @Override
        public Stability getStability() {
            return this.stability;
        }

        @Override
        public DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration registration) {
            return this.descriptionProviderFactory.apply(registration);
        }

        @Override
        public int getMinOccurs() {
            return this.minOccurance;
        }

        @Override
        public int getMaxOccurs() {
            return this.maxOccurance;
        }

        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            return this.accessConstraints;
        }

        @Override
        public boolean isRuntime() {
            return this.runtime;
        }

        @Override
        public boolean isOrderedChild() {
            return this.orderedChild;
        }

        @Override
        public boolean isFeature() {
            return this.feature;
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        }

        @Override
        public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        }

        @Override
        public void registerAdditionalRuntimePackages(ManagementResourceRegistration registration) {
        }
    }
}
