/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * Handler for an individual step in the overall execution of a management operation.
 * <p>
 * A handler is associated with a single {@link org.jboss.as.controller.OperationContext.Stage stage} of operation execution
 * and should only perform functions appropriate for that stage.
 * <p>
 * A handler {@link org.jboss.as.controller.registry.ManagementResourceRegistration#registerOperationHandler(OperationDefinition, OperationStepHandler) registered with a ManagementResourceRegistration}
 * will execute in {@link org.jboss.as.controller.OperationContext.Stage#MODEL}; otherwise the step will execute in the
 * stage passed to {@link org.jboss.as.controller.OperationContext#addStep(OperationStepHandler, org.jboss.as.controller.OperationContext.Stage)}
 * when it was added.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@FunctionalInterface
public interface OperationStepHandler {

    /**
     * Execute this step.  If the operation fails, {@link OperationContext#getFailureDescription() context.getFailureDescription()}
     * must be called, or an {@link OperationFailedException} must be thrown.
     * If the operation succeeded and the operation provides a return value, {@link OperationContext#getResult() context.getResult()} should
     * be called and the result populated with the outcome. If the handler wishes to take further action once the result
     * of the overall operation execution is known, one of the
     * {@link org.jboss.as.controller.OperationContext#completeStep(OperationContext.ResultHandler) context.completeStep variants}
     * should be called to register a callback. The callback will not be invoked if this method throws an exception.
     * <p>When this method is invoked the {@link Thread#getContextClassLoader() thread context classloader} will
     * be set to be the defining class loader of the class that implements this interface.</p>
     *
     * @param context the operation context
     * @param operation the operation being executed
     * @throws OperationFailedException if the operation failed <b>before</b> calling {@code context.completeStep()}
     */
    void execute(OperationContext context, ModelNode operation) throws OperationFailedException;
}
