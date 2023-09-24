/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.operations.deployment.SyncModelParameters;
import org.jboss.as.host.controller.mgmt.HostInfo;
import org.jboss.dmr.ModelNode;

/**
 * Operation handler synchronizing the domain model. This handler will calculate the operations needed to create
 * the local model and pass them to the {@code SyncModelOperationHandler}.
 *
 * This handler will be called for the initial host registration as well when reconnecting and tries to sync the complete
 * model, automatically ignoring unused resources if configured.
 *
 * @author Emanuel Muckenhuber
 */
public class SyncDomainModelOperationHandler extends SyncModelHandlerBase {

    private final HostInfo hostInfo;
    private final ExtensionRegistry extensionRegistry;

    public SyncDomainModelOperationHandler(HostInfo hostInfo,
                                           SyncModelParameters parameters) {
        super(parameters);
        this.hostInfo = hostInfo;
        this.extensionRegistry = parameters.getExtensionRegistry();
    }

    @Override
    Transformers.ResourceIgnoredTransformationRegistry createRegistry(OperationContext context, Resource remoteModel, Set<String> remoteExtensions) {
        final ReadMasterDomainModelUtil.RequiredConfigurationHolder rc =
                ReadMasterDomainModelUtil.populateHostResolutionContext(hostInfo, remoteModel, extensionRegistry);
        return ReadMasterDomainModelUtil.createHostIgnoredRegistry(hostInfo, rc);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        //Indicate to the IgnoredClonedProfileRegistry that we should clear the registry
        getParameters().getIgnoredResourceRegistry().getIgnoredClonedProfileRegistry().initializeModelSync();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                SyncDomainModelOperationHandler.super.execute(context, operation);
            }
        }, OperationContext.Stage.MODEL, true);

        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                getParameters().getIgnoredResourceRegistry().getIgnoredClonedProfileRegistry().complete(resultAction == OperationContext.ResultAction.ROLLBACK);
            }
        });
    }
}
