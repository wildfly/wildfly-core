/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.undeploy;

import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.capture.ServiceValueExecutorRegistry;

/**
 * Handles undeployment from the runtime.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentUndeployHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = UNDEPLOY;
    private final ServiceValueExecutorRegistry<DeploymentUnit> deploymentUnitRegistry;
    private final ServiceValueExecutorRegistry<Supplier<DeploymentStatus>> deploymentStatusRegistry;

    public DeploymentUndeployHandler(ServiceValueExecutorRegistry<DeploymentUnit> deploymentUnitRegistry,
                                     ServiceValueExecutorRegistry<Supplier<DeploymentStatus>> deploymentStatusRegistry) {
        assert deploymentUnitRegistry != null : "Null deploymentUnitRegistry";
        assert deploymentStatusRegistry != null : "Null deploymentStatusRegistry";
        this.deploymentUnitRegistry = deploymentUnitRegistry;
        this.deploymentStatusRegistry = deploymentStatusRegistry;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
        if (model.hasDefined(ENABLED.getName()) && model.get(ENABLED.getName()).asBoolean()) {
            final String runtimeName = RUNTIME_NAME.resolveModelAttribute(context, model).asString();
            model.get(ENABLED.getName()).set(false);

            final String managementName = context.getCurrentAddressValue();

            undeploy(context, operation, managementName, runtimeName, deploymentUnitRegistry, deploymentStatusRegistry);
        }
        DeploymentUtils.disableAttribute(model);
    }
}
