/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deploymentoverlay;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.deploymentoverlay.DeploymentOverlayModel.REMOVED_CONTENTS;

import java.util.HashSet;
import java.util.Set;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.transform.TransformerOperationAttachment;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;

/**
 * Handles removal of a deployment overlay content from the model.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class DeploymentOverlayContentRemove implements OperationStepHandler {

    private final ContentRepository contentRepository;

    public DeploymentOverlayContentRemove(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        Set<PathAddress> removed = TransformerOperationAttachment.getOrCreate(context).getAttachment(REMOVED_CONTENTS);
        if (removed == null) {
            removed = new HashSet<>();
            TransformerOperationAttachment.getOrCreate(context).attach(REMOVED_CONTENTS, removed);
        }
        removed.add(context.getCurrentAddress());
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        ModelNode model = context.readResourceFromRoot(address, false).getModel();
        final byte[] hash;
        if (model.has(CONTENT)) {
            hash = model.get(CONTENT).asBytes();
        } else {
            hash = null;
        }
        context.removeResource(PathAddress.EMPTY_ADDRESS);
        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                if (resultAction != OperationContext.ResultAction.ROLLBACK) {
                    //check that if this is a server group level op the referenced deployment overlay exists
                    if (hash != null) {
                        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
                        contentRepository.removeContent(ModelContentReference.fromModelAddress(address, hash));
                    }
                }
            }
        });
    }
}
