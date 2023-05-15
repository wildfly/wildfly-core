/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintUtilizationRegistry;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.wildfly.common.Assert;

/**
 * A registration for a management resource which consists of a resource description plus registered operation handlers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ManagementResourceRegistration extends ImmutableManagementResourceRegistration {

    /**
     * Get a specifically named resource that overrides this {@link PathElement#WILDCARD_VALUE wildcard registration}
     * by adding additional attributes, operations or child types.
     *
     * @param name the specific name of the resource. Cannot be {@code null} or {@link PathElement#WILDCARD_VALUE}
     *
     * @return the resource registration, <code>null</code> if there is none
     *
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    ManagementResourceRegistration getOverrideModel(String name);

    /**
     * Get a sub model registration.
     * <p>This method overrides the superinterface method of the same name in order to require
     * that the returned registration be mutable.
     * </p>
     *
     * @param address the address, relative to this node
     * @return the resource registration, <code>null</code> if there is none
     *
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    @Override
    ManagementResourceRegistration getSubModel(PathAddress address);

    /**
     * Register the existence of an addressable sub-resource of this resource. Before this method returns the provided
     * {@code resourceDefinition} will be given the opportunity to
     * {@link ResourceDefinition#registerAttributes(ManagementResourceRegistration) register attributes},
     * {@link ResourceDefinition#registerOperations(ManagementResourceRegistration) register operations},
     * and {@link ResourceDefinition#registerNotifications(ManagementResourceRegistration) register notifications}
     *
     * @param resourceDefinition source for descriptive information describing this
     *                            portion of the model (must not be {@code null})
     * @return a resource registration which may be used to add attributes, operations, notifications and sub-models
     *
     * @throws IllegalArgumentException if a submodel is already registered at {@code address}
     * @throws IllegalStateException if {@link #isRuntimeOnly()} returns {@code true}
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    ManagementResourceRegistration registerSubModel(ResourceDefinition resourceDefinition);

    /**
     * Unregister the existence of an addressable sub-resource of this resource.
     *
     * @param address the child of this registry that should no longer be available
     *
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void unregisterSubModel(PathElement address);

    /**
     * Gets whether this registration will always throw an exception if
     * {@link #registerOverrideModel(String, OverrideDescriptionProvider)} is invoked. An exception will always
     * be thrown for root resource registrations, {@link PathElement#WILDCARD_VALUE non-wildcard registrations}, or
     * {@link #isRemote() remote registrations}.
     *
     * @return {@code true} if an exception will not always be thrown; {@code false} if it will
     *
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    boolean isAllowsOverride();


    /**
     * Register a specifically named resource that overrides this {@link PathElement#WILDCARD_VALUE wildcard registration}
     * by adding additional attributes, operations or child types.
     *
     * @param name the specific name of the resource. Cannot be {@code null} or {@link PathElement#WILDCARD_VALUE}
     * @param descriptionProvider provider for descriptions of the additional attributes or child types
     *
     * @return a resource registration which may be used to add attributes, operations and sub-models
     *
     * @throws IllegalArgumentException if either parameter is null or if there is already a registration under {@code name}
     * @throws IllegalStateException if {@link #isRuntimeOnly()} returns {@code true} or if {@link #isAllowsOverride()} returns false
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    ManagementResourceRegistration registerOverrideModel(final String name, final OverrideDescriptionProvider descriptionProvider);

    /**
     * Unregister a specifically named resource that overrides a {@link PathElement#WILDCARD_VALUE wildcard registration}
     * by adding additional attributes, operations or child types.
     *
     * @param name the specific name of the resource. Cannot be {@code null} or {@link PathElement#WILDCARD_VALUE}
     *
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void unregisterOverrideModel(final String name);

    /**
     * Register an operation handler for this resource.
     *
     * @param definition the definition of operation
     * @param handler    the operation handler
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler);

    /**
     * Register an operation handler for this resource.
     *
     * @param definition the definition of operation
     * @param handler    the operation handler
     * @param inherited  {@code true} if the operation is inherited to child nodes, {@code false} otherwise
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited);

    /**
     * Unregister an operation handler for this resource.
     *
     * @param operationName       the operation name
     * @throws IllegalArgumentException if operationName is not registered
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void unregisterOperationHandler(final String operationName);


    /**
     * Records that the given attribute can be both read from and written to, and
     * provides operation handlers for the read and the write. The attribute is assumed to be
     * {@link org.jboss.as.controller.registry.AttributeAccess.Storage#CONFIGURATION} unless parameter
     * {@code flags} includes {@link org.jboss.as.controller.registry.AttributeAccess.Flag#STORAGE_RUNTIME}.
     *
     * @param definition the attribute definition. Cannot be {@code null}
     * @param readHandler the handler for attribute reads. May be {@code null}
     *                    in which case the default handling is used
     * @param writeHandler the handler for attribute writes. Cannot be {@code null}
     *
     * @throws IllegalArgumentException if {@code definition} or {@code writeHandler} are {@code null}
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerReadWriteAttribute(AttributeDefinition definition, OperationStepHandler readHandler, OperationStepHandler writeHandler);

    /**
     * Records that the given attribute can be read from but not written to, and
     * optionally provides an operation handler for the read. The attribute is assumed to be
     * {@link org.jboss.as.controller.registry.AttributeAccess.Storage#CONFIGURATION} unless parameter
     * {@code flags} includes {@link org.jboss.as.controller.registry.AttributeAccess.Flag#STORAGE_RUNTIME}.
     *
     * @param definition the attribute definition. Cannot be {@code null}
     * @param readHandler the handler for attribute reads. May be {@code null}
     *                    in which case the default handling is used
     *
     * @throws IllegalArgumentException if {@code definition} is {@code null}
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerReadOnlyAttribute(AttributeDefinition definition, OperationStepHandler readHandler);

    /**
     * Records that the given attribute is a metric.
     *
     * @param definition the attribute definition. Cannot be {@code null}
     * @param metricHandler the handler for attribute reads. Cannot be {@code null}
     *
     * @throws IllegalArgumentException if {@code definition} or {@code metricHandler} are {@code null}
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler);


    /**
     * Remove that the given attribute if present.
     *
     * @param attributeName the name of the attribute. Cannot be {@code null}
     *
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void unregisterAttribute(String attributeName);

    /**
     * Register a proxy controller.
     *
     * @param address the child of this registry that should be proxied
     * @param proxyController the proxy controller
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerProxyController(PathElement address, ProxyController proxyController);

    /**
     * Unregister a proxy controller
     *
     * @param address the child of this registry that should no longer be proxied
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void unregisterProxyController(PathElement address);

    /**
     * Register an alias registration to another part of the model
     *
     * @param address the child of this registry that is an alias
     * @param aliasEntry the target model
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerAlias(PathElement address, AliasEntry aliasEntry);

    /**
     * Unregister an alias
     *
     * @param address the child of this registry that is an alias
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void unregisterAlias(PathElement address);

    /**
     * Record that the given notification can be emitted by this resource.
     *
     * @param notification the definition of the notification. Cannot be {@code null}
     * @param inherited  {@code true} if the notification is inherited to child nodes, {@code false} otherwise
     *
     * @throws IllegalArgumentException if {@code notification} is {@code null}
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerNotification(NotificationDefinition notification, boolean inherited);

    /**
     * Record that the given notification can be emitted by this resource.
     *
     * The notification is not inherited by child nodes.
     *
     * @param notification the definition of the notification. Cannot be {@code null}
     *
     * @throws IllegalArgumentException if {@code notificationType} or {@code notificationEntry} is {@code null}
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void registerNotification(NotificationDefinition notification);

    /**
     * Remove that the given notification can be emitted by this resource.
     *
     * @param notificationType the type of the notification. Cannot be {@code null}
     *
     * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
     */
    void unregisterNotification(String notificationType);

    /**
     * Registers passed capability on resource
     * @param capability a capability to register
     */
    void registerCapability(RuntimeCapability capability);

    /**
     * Registers a set of capabilities that this resource does not directly provide but to which it contributes. This
     * will only include capabilities for which this resource <strong>does not</strong> control the
     * {@link #registerCapability(RuntimeCapability) registration of the capability}. Any capabilities registered by
     * this resource will instead be included in the return value for {@link #getCapabilities()}.
     * <p>
     * Use of this method is only necessary if the caller wishes to specifically record capability incorporation,
     * instead of relying on the default resolution mechanism detailed in {@link #getIncorporatingCapabilities()}, or
     * if it wishes disable the default resolution mechanism and specifically declare that this resource does not
     * contribute to parent capabilities. It does the latter by passing an empty set as the {@code capabilities}
     * parameter. Passing an empty set is not necessary if this resource itself directly
     * {@link #registerCapability(RuntimeCapability) provides a capability}, as it is the contract of
     * {@link #getIncorporatingCapabilities()} that in that case it must return an empty set.
     *
     * @param  capabilities set of capabilities, or {@code null} if default resolution of capabilities to which this
     *                      resource contributes should be used; an empty set can be used to indicate this resource
     *                      does not contribute to capabilities provided by its parent
     */
    void registerIncorporatingCapabilities(Set<RuntimeCapability> capabilities);

    /**
     * Registers a set of CapabilityReferenceRecorder. Each recorder is a link between acapability requirement and
     * the resource capability requiring it.
     * @param requirements a set of CapabilityReferenceRecorder.
     */
    void registerRequirements(Set<? extends CapabilityReferenceRecorder> requirements);

    /**
     * Register
     * {@link org.jboss.as.controller.registry.RuntimePackageDependency}
     * additional packages.
     *
     * @param pkgs The packages.
     */
    void registerAdditionalRuntimePackages(RuntimePackageDependency... pkgs);

    /**
     * A factory for creating a new, root model node registration.
     */
    class Factory {

        private final ProcessType processType;

        private Factory(ProcessType processType) {
            this.processType = processType;
        }

        /**
         * Returns a ManagementResourceRegistration's Factory that will use the specified {@code processType}
         * to determine whether resource metrics are registered or not.
         *
         * If the {@code processType} id {@code null}, metrics are <em>always</em> registered.
         *
         * @param processType can be {@code null}

         * @return a Factory which creates ManagementResourceRegistration that
         * dynamically determine whether resource metrics are actually registered
         */
        public static Factory forProcessType(ProcessType processType) {
            return new Factory(processType);
        }

        /**
         * Create a new root model node registration.
         *
         * @param resourceDefinition the facotry for the model description provider for the root model node
         * @return the new root model node registration
         *
         * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
         */
        public ManagementResourceRegistration createRegistration(final ResourceDefinition resourceDefinition) {
            return createRegistration(resourceDefinition, null, null);
        }

        /**
         * Create a new root model node registration.
         *
         * @param resourceDefinition the facotry for the model description provider for the root model node
         * @param constraintUtilizationRegistry registry for recording access constraints. Can be {@code null} if
         *                                      tracking access constraint usage is not supported
         * @param registry the capability registry (can be {@code null})
         * @return the new root model node registration
         *
         * @throws SecurityException if the caller does not have {@link ImmutableManagementResourceRegistration#ACCESS_PERMISSION}
         */
        public ManagementResourceRegistration createRegistration(final ResourceDefinition resourceDefinition,
                                                                 AccessConstraintUtilizationRegistry constraintUtilizationRegistry,
                                                                 CapabilityRegistry registry) {
            Assert.checkNotNullParam("resourceDefinition", resourceDefinition);
            ConcreteResourceRegistration resourceRegistration =
                    new ConcreteResourceRegistration(resourceDefinition, constraintUtilizationRegistry, registry, processType);
            resourceDefinition.registerAttributes(resourceRegistration);
            resourceDefinition.registerOperations(resourceRegistration);
            resourceDefinition.registerChildren(resourceRegistration);
            resourceDefinition.registerCapabilities(resourceRegistration);
            resourceDefinition.registerNotifications(resourceRegistration);
            resourceDefinition.registerAdditionalRuntimePackages(resourceRegistration);
            return resourceRegistration;
        }
    }
}
