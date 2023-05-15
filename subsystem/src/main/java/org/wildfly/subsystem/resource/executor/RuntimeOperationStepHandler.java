/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.executor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;

/**
 * Generic {@link org.jboss.as.controller.OperationStepHandler} for runtime operations.
 * @author Paul Ferraro
 */
public class RuntimeOperationStepHandler<C> extends AbstractRuntimeOnlyHandler implements ManagementResourceRegistrar {

    private final Map<String, RuntimeOperation<C>> operations = new HashMap<>();
    private final RuntimeOperationExecutor<C> executor;

    public <E extends Enum<E> & RuntimeOperation<C>> RuntimeOperationStepHandler(RuntimeOperationExecutor<C> executor, Class<E> operationClass) {
        this(executor, EnumSet.allOf(operationClass));
    }

    public RuntimeOperationStepHandler(RuntimeOperationExecutor<C> executor, Iterable<? extends RuntimeOperation<C>> operations) {
        this.executor = executor;
        for (RuntimeOperation<C> executable : operations) {
            this.operations.put(executable.getName(), executable);
        }
    }

    @Override
    public void register(ManagementResourceRegistration registration) {
        for (RuntimeOperation<C> operation : this.operations.values()) {
            registration.registerOperationHandler(operation.getOperationDefinition(), this);
        }
    }

    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) {
        String name = operation.get(ModelDescriptionConstants.OP).asString();
        RuntimeOperation<C> executable = this.operations.get(name);
        try {
            ModelNode result = this.executor.execute(context, operation, executable);
            if (result != null) {
                context.getResult().set(result);
            }
        } catch (OperationFailedException e) {
            context.getFailureDescription().set(e.getLocalizedMessage());
        }
        context.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
    }
}
