/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REDEPLOY;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.dmr.ModelNode;

/**
 * Handles redeployment in the runtime.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerGroupDeploymentRedeployHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REDEPLOY;

    public static final ServerGroupDeploymentRedeployHandler INSTANCE = new ServerGroupDeploymentRedeployHandler();

    private ServerGroupDeploymentRedeployHandler() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // We do nothing. This operation is really handled at the server level.
    }
}
