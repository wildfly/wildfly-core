/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
/**
 * Handles undeployment from the runtime.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentUndeployHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = UNDEPLOY;

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
        if (model.hasDefined(ENABLED.getName()) && model.get(ENABLED.getName()).asBoolean()) {
            final String runtimeName = RUNTIME_NAME.resolveModelAttribute(context, model).asString();
            model.get(ENABLED.getName()).set(false);

            final String managementName = context.getCurrentAddressValue();

            DeploymentHandlerUtil.undeploy(context, operation, managementName, runtimeName);
        }
        DeploymentUtils.disableAttribute(model);
    }
}
