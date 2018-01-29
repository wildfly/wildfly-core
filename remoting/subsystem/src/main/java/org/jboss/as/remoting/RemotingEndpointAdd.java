/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2014, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.jboss.as.remoting;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Add handler that adds a placeholder resource if not present, but otherwise just converts
 * any parameter values into write-attribute ops against the parent.
 *
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 * @author Brian Stansberry
 */
class RemotingEndpointAdd extends ModelOnlyAddStepHandler {

    @Override
    protected Resource createResource(OperationContext context) {
        // If the resource is already there, just use it
        // We do this because RemotingSubsystemAdd/WorkerThreadPoolVsEndpointHandler will end up adding a resource if
        // one isn't added in the same op. So if a user adds one in a separate op, we're forgiving about it.
        // Mostly we do this to allow transformers tests to pass which call ModelTestUtils.checkFailedTransformedBootOperations
        // which calls this OSH in a separate op from the one that calls RemotingSubystemAdd
        try {
            return context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        } catch (Resource.NoSuchResourceException ignored) {
            //
        }
        return super.createResource(context);
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

        // For any attribute where the value in 'operation' differs from the model in the
        // parent resource, add an immediate 'write-attribute' step against the parent
        PathAddress parentAddress = context.getCurrentAddress().getParent();
        ModelNode parentModel = context.readResourceFromRoot(parentAddress, false).getModel();
        OperationStepHandler writeHandler = null;
        ModelNode baseWriteOp = null;
        for (AttributeDefinition ad : RemotingEndpointResource.ATTRIBUTES.values()) {
            String attr = ad.getName();
            ModelNode parentVal = parentModel.get(attr);
            ModelNode opVal = operation.has(attr) ? operation.get(attr) : new ModelNode();
            if (!parentVal.equals(opVal)) {
                if (writeHandler == null) {
                    writeHandler = context.getRootResourceRegistration().getOperationHandler(parentAddress, WRITE_ATTRIBUTE_OPERATION);
                    baseWriteOp = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, parentAddress);
                }
                ModelNode writeOp = baseWriteOp.clone();
                writeOp.get(NAME).set(attr);
                writeOp.get(VALUE).set(opVal);
                context.addStep(writeOp, writeHandler, OperationContext.Stage.MODEL, true);
            }
        }
    }
}
