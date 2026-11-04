/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * Interface that can be implemented by {@code read-resource} or {@code read-attribute} operation handlers that a
 * {@code ResourceDefinition}
 * {@link ManagementResourceRegistration#registerOperationHandler(OperationDefinition, OperationStepHandler)
 * registers as an operation}, instead of using the global {@code read-resource} handler or following the standard
 * practice of registering a {@code read-attribute} handler as part of attribute registration, (e.g. via
 * {@link ManagementResourceRegistration#registerReadWriteAttribute(AttributeDefinition, OperationStepHandler, OperationStepHandler)}).
 * <p/>
 * Implementing this interface allows the handler for a {@code read-resource} operation to incorporate any RBAC
 * access filtering information from reading its attributes or child resources in the overall data filtering response.
 */
public interface FilteredDataReadHandler {

    /**
     * Execute a read, recording any RBAC filtering information in the given {@code filteredData}. The semantics
     * of the read execution should follow those of {@link OperationStepHandler#execute(OperationContext, ModelNode)}.
     *
     * @param context      the operation context
     * @param operation    the operation being executed
     * @param filteredData collection point for information about data filtered from. Will not be {@code null}.
     * @throws OperationFailedException if the operation failed <b>before</b> calling {@code context.completeStep()}
     */
    void execute(OperationContext context, ModelNode operation, FilteredData filteredData) throws OperationFailedException;
}
