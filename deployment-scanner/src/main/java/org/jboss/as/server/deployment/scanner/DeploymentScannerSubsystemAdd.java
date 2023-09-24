/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.dmr.ModelNode;

/**
 * Handles the addition of the deployment scanning subsystem.
 *
 * @author Emanuel Muckenhuber
 */
public class DeploymentScannerSubsystemAdd extends AbstractAddStepHandler {
    static final DeploymentScannerSubsystemAdd INSTANCE = new DeploymentScannerSubsystemAdd();

    private DeploymentScannerSubsystemAdd() {
        //
    }

    protected void populateModel(ModelNode operation, ModelNode model) {
        model.get(CommonAttributes.SCANNER).setEmptyObject();
    }

    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
