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
package org.jboss.as.domain.controller.transformers;

import java.util.Set;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import static org.jboss.as.controller.client.helpers.ClientConstants.STEPS;
import org.jboss.as.controller.client.helpers.Operations;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayModel;
import static org.jboss.as.server.deploymentoverlay.DeploymentOverlayModel.REMOVED_CONTENTS;
import static org.jboss.as.server.deploymentoverlay.DeploymentOverlayModel.REMOVED_LINKS;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
class DeploymentOverlayTransformers {

     public static ChainedTransformationDescriptionBuilder buildTransformerChain(ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder chainedBuilder =
                TransformationDescriptionBuilder.Factory.createChainedInstance(DeploymentOverlayModel.DEPLOYMENT_OVERRIDE_PATH, currentVersion);

        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(currentVersion, DomainTransformers.VERSION_1_6);
        builder.addOperationTransformationOverride(REMOVE)
            .setCustomOperationTransformer((TransformationContext context, PathAddress address, ModelNode operation) -> {
                Set<PathAddress> removedContents = context.getAttachment(REMOVED_CONTENTS);
                    if (removedContents != null && !removedContents.isEmpty()) {
                        ModelNode compositeOp = Operations.createCompositeOperation();
                        for (PathAddress removed : removedContents) {
                            if (address.equals(removed.subAddress(0, address.size()))) {
                                compositeOp.get(STEPS).add(Operations.createRemoveOperation(removed.toModelNode()));
                            }
                        }
                        compositeOp.get(STEPS).add(operation);
                        return new TransformedOperation(compositeOp, OperationResultTransformer.ORIGINAL_RESULT);
                    }
                return new TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
        }).end();
        return chainedBuilder;
    }

     public static void registerServerGroupTransformers1_6_AndBelow(ResourceTransformationDescriptionBuilder parent) {
        parent.addChildResource(DeploymentOverlayModel.DEPLOYMENT_OVERRIDE_PATH)
                .addOperationTransformationOverride(REMOVE)
                .setCustomOperationTransformer((TransformationContext context, PathAddress address, ModelNode operation) -> {
                    Set<PathAddress> removedLinks = context.getAttachment(REMOVED_LINKS);
                    if (removedLinks != null && !removedLinks.isEmpty()) {
                        ModelNode compositeOp = Operations.createCompositeOperation();
                        for (PathAddress removed : removedLinks) {
                            if (address.equals(removed.subAddress(0, address.size()))) {
                                compositeOp.get(STEPS).add(Operations.createRemoveOperation(removed.toModelNode()));
                            }
                        }
                        compositeOp.get(STEPS).add(operation);
                        return new TransformedOperation(compositeOp, OperationResultTransformer.ORIGINAL_RESULT);
                    }
                    return new TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
                })
                .end();
    }
}
