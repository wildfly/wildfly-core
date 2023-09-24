/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.ignored;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles add of an ignored domain resource type.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class IgnoredDomainTypeAddHandler implements OperationStepHandler {

    IgnoredDomainTypeAddHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String type = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        if (ModelDescriptionConstants.HOST.equals(type)) {
            throw HostControllerLogger.ROOT_LOGGER.cannotIgnoreTypeHost(ModelDescriptionConstants.HOST);
        }

        ModelNode names = IgnoredDomainTypeResourceDefinition.NAMES.validateOperation(operation);
        ModelNode wildcardNode = IgnoredDomainTypeResourceDefinition.WILDCARD.validateOperation(operation);
        Boolean wildcard = wildcardNode.isDefined() ? wildcardNode.asBoolean() : null;
        final IgnoreDomainResourceTypeResource resource = new IgnoreDomainResourceTypeResource(type, names, wildcard);
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);

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
