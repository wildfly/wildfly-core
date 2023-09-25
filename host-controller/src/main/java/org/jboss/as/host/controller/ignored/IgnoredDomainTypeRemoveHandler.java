/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.ignored;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * Handles remove of an ignored domain resource type.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class IgnoredDomainTypeRemoveHandler implements OperationStepHandler {

    IgnoredDomainTypeRemoveHandler() {
    }


    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final IgnoreDomainResourceTypeResource resource =
                IgnoreDomainResourceTypeResource.class.cast(context.removeResource(PathAddress.EMPTY_ADDRESS));

        final boolean booting = context.isBooting();
        if (!booting) {
            context.reloadRequired();
        }

        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                if (resultAction == OperationContext.ResultAction.KEEP) {
                    if (booting) {
                        resource.publish();
                    }
                } else if (!booting) {
                    context.revertReloadRequired();
                }
            }
        });
    }
}
