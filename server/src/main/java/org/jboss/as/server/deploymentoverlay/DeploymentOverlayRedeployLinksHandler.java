/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.server.deploymentoverlay;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REDEPLOY_LINKS;

import java.util.HashSet;
import java.util.Set;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.dmr.ModelNode;

/**
 * Handler that will redeploy the deployments linked to an overlay.
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class DeploymentOverlayRedeployLinksHandler implements OperationStepHandler {

    private static final StringListAttributeDefinition RUNTIME_NAMES_DEFINITION
            = new StringListAttributeDefinition.Builder("deployments")
                    .setRequired(false)
                    .build();

    public static final OperationDefinition REDEPLOY_LINKS_DEFINITION = new SimpleOperationDefinitionBuilder(
            REDEPLOY_LINKS, ControllerResolver.getResolver(ModelDescriptionConstants.DEPLOYMENT_OVERLAY))
            .addParameter(RUNTIME_NAMES_DEFINITION)
            .build();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        Set<String> runtimeNames = AffectedDeploymentOverlay.listLinks(context, context.getCurrentAddress());
        if (operation.hasDefined(RUNTIME_NAMES_DEFINITION.getName())) {
            Set<String> requiredRuntimeNames = new HashSet<>(RUNTIME_NAMES_DEFINITION.unwrap(context, operation));
            if (!requiredRuntimeNames.isEmpty()) {
                runtimeNames = requiredRuntimeNames;
            }
        }
        // Adding a redeploy operation step for each runtime.
        AffectedDeploymentOverlay.redeployLinks(context, context.getCurrentAddress().getParent(), runtimeNames);
    }
}
