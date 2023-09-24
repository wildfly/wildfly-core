/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.List;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Provides essential information defining a management resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ResourceDefinition {

    /**
     * Gets the path element that describes how to navigate to this resource from its parent resource, or {@code null}
     * if this is a definition of a root resource.
     *
     * @return the path element, or {@code null} if this is a definition of a root resource.
     */
    PathElement getPathElement();

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
    default void registerCapabilities(final ManagementResourceRegistration resourceRegistration){
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
}
