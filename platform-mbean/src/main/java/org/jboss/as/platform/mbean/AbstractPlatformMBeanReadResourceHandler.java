/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.PlatformManagedObject;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.ResourceNotAddressableException;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.global.FilteredData;
import org.jboss.as.controller.operations.global.FilteredDataReadHandler;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.global.ReadResourceAssemblyHandler;
import org.jboss.as.controller.operations.global.ReadResourceHandler;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Base class for {@code read-resource} handlers for platform mbean resources where the default
 * {@link ReadResourceHandler} is not suitable. The base class ensures RBAC requirements are properly implemented.
 * <p/>
 * <strong>Note:</strong>The main platform mbean use case not using the default {@code read-resource} handler is the
 * longstanding behavior where we ignore UnsupportedOperationException failures reading Platform MBean properties in
 * {@code read-resource} but we fail {@code read-attribute} operations. The rationale there being a
 * {@code read-attribute} call expresses specific intent to get the value, while a {@code read-resource} is a more
 * general request to be given what is available. If a particular JVM implementor doesn't support something, we want
 * callers to know that if they specifically ask, but we don't want {@code read-resource} to be unusable.
 * The default {@link ReadResourceHandler} doesn't support this semantic, because it executes a {@code read-attribute}
 * step for each resource attribute, and if those fail the operation fails.
 *
 * @param <T> the type of the {@link PlatformManagedObject} represented by the resource
 */
abstract class AbstractPlatformMBeanReadResourceHandler<T extends PlatformManagedObject>
        implements OperationStepHandler, FilteredDataReadHandler {

    // Track if the resource we're handling has any resource-level access constraints
    // (definitely not as of 2026/08/02) so if not we can skip resource-level RBAC checks
    private final boolean hasResourceAccessConstraints;

    AbstractPlatformMBeanReadResourceHandler(List<AccessConstraintDefinition> resourceConstaints) {
        this.hasResourceAccessConstraints = resourceConstaints != null && !resourceConstaints.isEmpty();
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        doExecute(context, operation, new FilteredData(context.getCurrentAddress()), true);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation, FilteredData filteredData) throws OperationFailedException {
        doExecute(context, operation, filteredData, false);
    }

    private void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData,
                           boolean reportFilteredData) throws OperationFailedException {

        ReadResourceHandler.Validator.NON_RESOLVABLE.validate(operation);

        if (hasResourceAccessConstraints) {
            // Ensure the caller has perms to address and read the resource
            AuthorizationResult authResult = context.authorize(operation,
                    EnumSet.of(Action.ActionEffect.ADDRESS, Action.ActionEffect.READ_RUNTIME));
            if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
                // See if the problem was addressability
                AuthorizationResult addressResult = context.authorize(operation,
                        EnumSet.of(Action.ActionEffect.ADDRESS));
                if (addressResult.getDecision() == AuthorizationResult.Decision.DENY) {
                    throw new ResourceNotAddressableException(context.getCurrentAddress());
                }
                throw ControllerLogger.ROOT_LOGGER.unauthorized(context.getCurrentOperationName(), context.getCurrentAddress(), authResult.getExplanation());
            }
        }

        // Attributes of AccessType.METRIC
        final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics = new HashMap<>();
        // Non-AccessType.METRIC attributes
        final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes = new HashMap<>();

        // Last to execute is the handler that assembles the overall response from the pieces created by all the other steps
        final ReadResourceAssemblyHandler assemblyHandler = new ReadResourceAssemblyHandler(context.getCurrentAddress(),
                metrics, otherAttributes, reportFilteredData, filteredData);
        context.addStep(assemblyHandler, OperationContext.Stage.VERIFY, true);

        executeAttributeReads(context, operation, metrics, otherAttributes, filteredData, getPlatformMBean());

    }

    /**
     * Gets the {@link PlatformManagedObject} to read.
     *
     * @return the {@link PlatformManagedObject}. May be {@code null} if the {@code executeAttributeReads} implementation
     *         can handle {@code null} values.
     */
    abstract T getPlatformMBean();

    /**
     * Read the resource's attribute values, storing them in the given maps for subsequent processing.
     * @param context the operation context. Will not be {@code null}.
     * @param operation the operation being executed. Will not be {@code null}
     * @param metrics               map of attributes of AccessType.METRIC. Keys are the attribute names, values are the full
     *                              read-attribute response from invoking the attribute's read handler. Will not be {@code null}
     * @param otherAttributes       map of attributes not of AccessType.METRIC that have a read handler registered. Keys
     *                              are the attribute names, values are the full read-attribute response from invoking the
     *                              attribute's read handler. Will not be {@code null}
     * @param filteredData          information about resources and attributes that were filtered. Will not be {@code null}
     * @param mbean an object returned by {@link #getPlatformMBean()}. May be {@code null} if that method returns {@code null}
     */
    abstract void executeAttributeReads(OperationContext context,
                                        ModelNode operation,
                                        Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics,
                                        Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes,
                                        FilteredData filteredData,
                                        T mbean) throws OperationFailedException;

    /**
     * Provides standard handling for reading a single attribute.
     *
     * @param context the operation context. Will not be {@code null}.
     * @param operation the operation being executed. Will not be {@code null}
     * @param attribute the name of the attribute to read. Will not be {@code null}
     * @param readFunction {@link BiFunction} to use for the actual read. Will not be {@code null}.
     * @param mbean  the {@code PlatformManagedObject} to pass to the {@code readFunction}. Will not be {@code null}
     * @param store map into which an entry for the attribute should be added
     * @param filteredData information about attributes that were filtered. Will not be {@code null}
     */
    void executeAttributeRead(OperationContext context, ModelNode operation, String attribute,
                              BiFunction<String, T, ModelNode> readFunction, T mbean,
                              Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> store,
                              FilteredData filteredData) {
        final ModelNode value = new ModelNode();
        try {
            value.set(readFunction.apply(attribute, mbean));
        } catch (SecurityException logged) {
            // BES 2026-08-02 we historically caught and ignored SecurityException in various platform mbean
            // read-resource handlers, but I don't see why one should be thrown for our use, and if one is that
            // indicates something unexpected. So log it.
            PlatformMBeanLogger.ROOT_LOGGER.debugf(logged, "Unexpected SecurityException reading %s from %s",
                    attribute, context.getCurrentAddress());

            // just leave value undefined
        } catch (UnsupportedOperationException ignored) {
            // just leave it undefined
        }

        // Confirm the read is allowed
        AuthorizationResult authorizationResult = context.authorize(operation, attribute, value);
        if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
            value.clear();
            filteredData.addReadRestrictedAttribute(context.getCurrentAddress(), attribute);
        }

        ModelNode response = new ModelNode();
        response.get(ModelDescriptionConstants.RESULT).set(value);
        store.put(new AttributeDefinition.NameAndGroup(attribute), new GlobalOperationHandlers.AvailableResponse(response));
    }
}
