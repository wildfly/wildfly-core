/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deploymentoverlay;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;

/**
 * @author Stuart Douglas
 */
public class DeploymentOverlayDeploymentAdd extends AbstractAddStepHandler {


    public DeploymentOverlayDeploymentAdd() {
        super(DeploymentOverlayDeploymentDefinition.attributes());
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }

}
