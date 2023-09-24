/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.dmr.ModelNode;

/**
 * Handler reading the transformed domain model.
 *
 * @author Emanuel Muckenhuber
 */
class ReadDomainModelHandler implements OperationStepHandler {

    private final Transformers transformers;
    private final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry;
    private final boolean lock;

    public ReadDomainModelHandler(final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry, final Transformers transformers, final boolean lock) {
        this.transformers = transformers;
        this.ignoredTransformationRegistry = ignoredTransformationRegistry != null ? ignoredTransformationRegistry : Transformers.DEFAULT;
        this.lock = lock;
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // if the calling process has already acquired a lock, don't relock.
        if (lock) {
            // Acquire the lock to make sure that nobody can modify the model before the slave has applied it
            context.acquireControllerLock();
        }

        final Transformers.TransformationInputs transformationInputs = new Transformers.TransformationInputs(context);
        final ReadMasterDomainModelUtil readUtil = ReadMasterDomainModelUtil.readMasterDomainResourcesForInitialConnect(transformers,
                transformationInputs, ignoredTransformationRegistry, transformationInputs.getRootResource());
        context.getResult().set(readUtil.getDescribedResources());
    }

}
