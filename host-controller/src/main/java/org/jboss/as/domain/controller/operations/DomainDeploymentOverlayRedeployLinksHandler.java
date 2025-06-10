/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY_LINK_REMOVAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REDEPLOY_LINKS;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.DEPLOYMENT_NAMECHECK_LOGGER;

import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.server.deploymentoverlay.AffectedDeploymentOverlay;
import org.jboss.dmr.ModelNode;

/**
 * Handler to redeploy deployments linked to an overlay in domain mode.
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class DomainDeploymentOverlayRedeployLinksHandler implements OperationStepHandler {

    public static final StringListAttributeDefinition RUNTIME_NAMES_DEFINITION =
            new StringListAttributeDefinition.Builder(DEPLOYMENTS)
                            .setRequired(false)
                            .build();

    public static final OperationDefinition REDEPLOY_LINKS_DEFINITION = new SimpleOperationDefinitionBuilder(
            REDEPLOY_LINKS, ControllerResolver.getResolver(ModelDescriptionConstants.DEPLOYMENT_OVERLAY))
            .addParameter(RUNTIME_NAMES_DEFINITION)
            .build();

    private final boolean domainRoot;

    public DomainDeploymentOverlayRedeployLinksHandler(boolean domainRoot) {
        this.domainRoot = domainRoot;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        Set<String> runtimeNames = checkRequiredRuntimeNames(context, operation);
        ModelNode removeOperation = null;
        if (isRedeployAfterRemoval(operation)) {
            assert !runtimeNames.isEmpty();
            assert runtimeNames.size() == 1;
            removeOperation = Operations.createRemoveOperation(context.getCurrentAddress().append(DEPLOYMENT, runtimeNames.iterator().next()).toModelNode());
        }
        if (domainRoot) {
            AffectedDeploymentOverlay.redeployLinksAndTransformOperationForDomain(context, runtimeNames, removeOperation);
        } else {
            AffectedDeploymentOverlay.redeployLinksAndTransformOperation(context, removeOperation, context.getCurrentAddress().getParent(), runtimeNames);
        }
        if (isRedeployAfterRemoval(operation)) {//Now that the redeploy operations are ready we can remove the resource.
            context.removeResource(PathAddress.pathAddress(DEPLOYMENT, runtimeNames.iterator().next()));
        }
    }

    private Set<String> checkRequiredRuntimeNames(OperationContext context, ModelNode operation) throws OperationFailedException {
        Set<String> runtimeNames = domainRoot ? AffectedDeploymentOverlay.listAllLinks(context, context.getCurrentAddressValue()) : AffectedDeploymentOverlay.listLinks(context, context.getCurrentAddress());
        if(operation.hasDefined(RUNTIME_NAMES_DEFINITION.getName())) {
            Set<String> requiredRuntimeNames = new HashSet<>(RUNTIME_NAMES_DEFINITION.unwrap(context, operation));
            if(!requiredRuntimeNames.isEmpty()) {
                runtimeNames = requiredRuntimeNames;
            }
        }

        for (String runtimeName : runtimeNames) {
//            if (!runtimeName.contains(".")) {
//                Set<String> deployments = AffectedDeploymentOverlay.listAllLinks()
//                context.resou
//                DEPLOYMENT_NAMECHECK_LOGGER.deploymentsRuntimeNameWithoutExtension(deploymentName, runtimeName);
//            }
            DEPLOYMENT_NAMECHECK_LOGGER.controlPrint("THIS IS RUNTIME " + runtimeName);
        }
        return runtimeNames;
    }

    /**
     * Check if this is a redeployment triggered after the removal of a link.
     * @param operation the current operation.
     * @return true if this is a redeploy after the removal of a link.
     * @see org.jboss.as.server.deploymentoverlay.DeploymentOverlayDeploymentRemoveHandler
     */
    private boolean isRedeployAfterRemoval(ModelNode operation) {
        return operation.hasDefined(DEPLOYMENT_OVERLAY_LINK_REMOVAL) &&
                operation.get(DEPLOYMENT_OVERLAY_LINK_REMOVAL).asBoolean();
    }
}
