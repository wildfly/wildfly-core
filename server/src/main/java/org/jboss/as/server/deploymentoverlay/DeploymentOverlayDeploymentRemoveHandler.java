/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deploymentoverlay;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY_LINK_REMOVAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REDEPLOY_AFFECTED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REDEPLOY_LINKS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.server.deploymentoverlay.DeploymentOverlayModel.REMOVED_LINKS;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.TransformerOperationAttachment;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Handler to remove a link between an overlay and a deployment with support to redeploy deployments thus affected.
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class DeploymentOverlayDeploymentRemoveHandler extends AbstractRemoveStepHandler {

    public static final AttributeDefinition REDEPLOY_AFFECTED_DEFINITION
            = SimpleAttributeDefinitionBuilder.create(REDEPLOY_AFFECTED, ModelType.BOOLEAN)
                    .setRequired(false)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();
    public static final OperationDefinition REMOVE_DEFINITION
            = new SimpleOperationDefinitionBuilder(REMOVE, ControllerResolver.getResolver(DEPLOYMENT_OVERLAY + '.' + DEPLOYMENT))
                    .addParameter(REDEPLOY_AFFECTED_DEFINITION)
                    .build();

    public static final DeploymentOverlayDeploymentRemoveHandler INSTANCE = new DeploymentOverlayDeploymentRemoveHandler();

    @Override
    protected void performRemove(OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final String runtimeName = context.getCurrentAddressValue();
            Set<PathAddress> removed = TransformerOperationAttachment.getOrCreate(context).getAttachment(REMOVED_LINKS);
            if (removed == null) {
                removed = new HashSet<>();
                TransformerOperationAttachment.getOrCreate(context).attach(REMOVED_LINKS, removed);
            }
            removed.add(context.getCurrentAddress());
            if (REDEPLOY_AFFECTED_DEFINITION.resolveModelAttribute(context, operation).asBoolean()) {
                if (SERVER_GROUP.equals(context.getCurrentAddress().getElement(0).getKey())) {
                    PathAddress overlayAddress = context.getCurrentAddress().getParent();
                    OperationStepHandler handler = context.getRootResourceRegistration().getOperationHandler(overlayAddress, REDEPLOY_LINKS);
                    ModelNode redeployAffectedOperation = Util.createOperation(REDEPLOY_LINKS, overlayAddress);
                    redeployAffectedOperation.get(DEPLOYMENTS).setEmptyList().add(runtimeName);
                    redeployAffectedOperation.get(DEPLOYMENT_OVERLAY_LINK_REMOVAL).set(true);
                    assert handler != null;
                    assert redeployAffectedOperation.isDefined();
                    context.addStep(redeployAffectedOperation, handler, OperationContext.Stage.MODEL, true);
                    return;
                } else {
                    Set<String> deploymentNames = AffectedDeploymentOverlay.listDeployments(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS), Collections.singleton(runtimeName));
                    AffectedDeploymentOverlay.redeployDeployments(context, PathAddress.EMPTY_ADDRESS, deploymentNames);
                }
            }
            super.performRemove(context, operation, model);
    }

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * {@inheritDoc}
     */
    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * {@inheritDoc}
     */
    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code false}.
     *
     * {@inheritDoc}
     */
    @Override
    protected final boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
