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
import org.jboss.dmr.ModelNode;

/**
 * Handles add of an ignored domain resource type.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class IgnoredDomainTypeWriteAttributeHandler implements OperationStepHandler {

    public IgnoredDomainTypeWriteAttributeHandler() {
    }


    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String attribute = operation.require(ModelDescriptionConstants.NAME).asString();

        ModelNode value = operation.get(ModelDescriptionConstants.VALUE);
        ModelNode mockOp = new ModelNode();
        mockOp.get(attribute).set(value);

        final IgnoreDomainResourceTypeResource resource =
            IgnoreDomainResourceTypeResource.class.cast(context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS));

        if (IgnoredDomainTypeResourceDefinition.NAMES.getName().equals(attribute)) {

            IgnoredDomainTypeResourceDefinition.NAMES.validateOperation(mockOp);

            resource.setNames(value);
        } else if (IgnoredDomainTypeResourceDefinition.WILDCARD.getName().equals(attribute)) {

            IgnoredDomainTypeResourceDefinition.WILDCARD.validateOperation(mockOp);
            ModelNode wildcardNode =  IgnoredDomainTypeResourceDefinition.WILDCARD.resolveModelAttribute(context, mockOp);
            Boolean wildcard = wildcardNode.isDefined() ? wildcardNode.asBoolean() : null;
            resource.setWildcard(wildcard);
        }

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
