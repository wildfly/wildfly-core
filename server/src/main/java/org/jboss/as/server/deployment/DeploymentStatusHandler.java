/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;

import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.service.capture.FunctionExecutor;
import org.wildfly.service.capture.FunctionExecutorRegistry;

/**
 * @author Jason T. Greene
 */
public final class DeploymentStatusHandler implements OperationStepHandler {

    private final FunctionExecutorRegistry<ServiceName, Supplier<DeploymentStatus>> executorRegistry;

    public DeploymentStatusHandler(FunctionExecutorRegistry<ServiceName, Supplier<DeploymentStatus>> executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ModelNode deployment = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        final boolean isEnabled = ENABLED.resolveModelAttribute(context, deployment).asBoolean();
        final String runtimeName = RUNTIME_NAME.resolveModelAttribute(context, deployment).asString();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) {
                final ModelNode result = context.getResult();
                if (!isEnabled) {
                    result.set(DeploymentStatus.STOPPED.toString());
                } else {
                    ServiceName statusServiceName = Services.deploymentStatusName(Services.deploymentUnitName(runtimeName));
                    FunctionExecutor<Supplier<DeploymentStatus>> functionExecutor = executorRegistry.getExecutor(statusServiceName);
                    if (functionExecutor != null) {
                        result.set(functionExecutor.execute(new StatusFunction()));
                    } else {
                        result.set(DeploymentStatus.STOPPED.toString());
                    }
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private static class StatusFunction implements ExceptionFunction<Supplier<DeploymentStatus>, String, RuntimeException> {

        @Override
        public String apply(Supplier<DeploymentStatus> supplier) throws RuntimeException {
            DeploymentStatus ds = supplier == null ? DeploymentStatus.STOPPED : supplier.get();
            return ds.toString();
        }
    }
}
