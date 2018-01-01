/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.remoting;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;

/**
 * Handles a remove of the configuration=endpoint resource by undefining all endpoint config attribute on the parent.
 *
 * @author Brian Stansberry
 */
class RemotingEndpointRemove extends ModelOnlyRemoveStepHandler {

    @Override
    protected void performRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        // For any attribute that is defined in the parent resource, add an immediate 'write-attribute'
        // step against the parent to undefine it
        PathAddress parentAddress = context.getCurrentAddress().getParent();
        ModelNode parentModel = context.readResourceFromRoot(parentAddress, false).getModel();
        OperationStepHandler writeHandler = null;
        ModelNode baseWriteOp = null;
        for (AttributeDefinition ad : RemotingEndpointResource.ATTRIBUTES.values()) {
            String attr = ad.getName();
            if (parentModel.hasDefined(attr)) {
                if (writeHandler == null) {
                    writeHandler = context.getRootResourceRegistration().getOperationHandler(parentAddress, WRITE_ATTRIBUTE_OPERATION);
                    baseWriteOp = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, parentAddress);
                }
                ModelNode writeOp = baseWriteOp.clone();
                writeOp.get(NAME).set(attr);
                writeOp.get(VALUE).set(new ModelNode());
                context.addStep(writeOp, writeHandler, OperationContext.Stage.MODEL, true);
            }
        }
    }
}
