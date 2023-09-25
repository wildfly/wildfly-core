/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.Identity;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.tool.PatchingHistory;
import org.jboss.as.patching.tool.PatchingHistory.Entry;
import org.jboss.dmr.ModelNode;

/**
 * This handler returns the info about specific patch
 *
 * @author Alexey Loubyansky
 */
public class PatchInfoHandler extends PatchStreamResourceOperationStepHandler {

    public static final PatchInfoHandler INSTANCE = new PatchInfoHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        String patchId = PatchResourceDefinition.PATCH_ID_OPTIONAL.resolveModelAttribute(context, operation).asStringOrNull();

        if (patchId != null) {
            super.execute(context, operation);
        } else {
            final ModelNode readResource = new ModelNode();
            readResource.get(OP_ADDR).set(operation.get(OP_ADDR));
            readResource.get(OP).set(READ_RESOURCE_OPERATION);
            readResource.get(RECURSIVE).set(true);
            readResource.get(INCLUDE_RUNTIME).set(true);
            final OperationStepHandler readResHandler = context.getRootResourceRegistration().getOperationHandler(PathAddress.EMPTY_ADDRESS, READ_RESOURCE_OPERATION);
            context.addStep(readResource, readResHandler, OperationContext.Stage.MODEL);
        }
    }

    @Override
    protected void execute(final OperationContext context, final ModelNode operation, final InstalledIdentity installedIdentity) throws OperationFailedException {

        String patchId = PatchResourceDefinition.PATCH_ID_OPTIONAL.resolveModelAttribute(context, operation).asStringOrNull();
        assert patchId != null;  // the overridden execute(OperationContext context, ModelNode operation) ensures not

        final boolean verbose = PatchResourceDefinition.VERBOSE.resolveModelAttribute(context, operation).asBoolean();
        final PatchableTarget.TargetInfo info;
        try {
            info = installedIdentity.getIdentity().loadTargetInfo();
        } catch (Exception e) {
            throw new OperationFailedException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(installedIdentity.getIdentity().getName()), e);
        }

        final PatchingHistory.Iterator i = PatchingHistory.Factory.iterator(installedIdentity, info);
        final ModelNode result = patchIdInfo(context, patchId, verbose, i);
        if (result == null) {
            context.getFailureDescription().set(PatchLogger.ROOT_LOGGER.patchNotFoundInHistory(patchId).getLocalizedMessage());
        } else {
            context.getResult().set(result);
        }

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    private ModelNode patchIdInfo(final OperationContext context, final String patchId, final boolean verbose, final PatchingHistory.Iterator i) {
        while(i.hasNext()) {
            final Entry entry = i.next();
            if(patchId.equals(entry.getPatchId())) {
                final ModelNode result = new ModelNode();
                result.get(Constants.PATCH_ID).set(entry.getPatchId());
                result.get(Constants.TYPE).set(entry.getType().getName());
                result.get(Constants.DESCRIPTION).set(entry.getMetadata().getDescription());
                final String link = entry.getMetadata().getLink();
                if (link != null) {
                    result.get(Constants.LINK).set(link);
                }
                final Identity identity = entry.getMetadata().getIdentity();
                result.get(Constants.IDENTITY_NAME).set(identity.getName());
                result.get(Constants.IDENTITY_VERSION).set(identity.getVersion());

                if(verbose) {
                    final ModelNode list = result.get(Constants.ELEMENTS).setEmptyList();
                    final Patch metadata = entry.getMetadata();
                    for(PatchElement e : metadata.getElements()) {
                        final ModelNode element = new ModelNode();
                        element.get(Constants.PATCH_ID).set(e.getId());
                        element.get(Constants.TYPE).set(e.getProvider().isAddOn() ? Constants.ADD_ON : Constants.LAYER);
                        element.get(Constants.NAME).set(e.getProvider().getName());
                        element.get(Constants.DESCRIPTION).set(e.getDescription());
                        list.add(element);
                    }
                }
                return result;
            }
        }
        return null;
    }
}
