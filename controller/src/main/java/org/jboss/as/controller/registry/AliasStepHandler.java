/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.controller.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AliasEntry.AliasContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A handler that simply maps an alias onto a target part of the model.
 *
 */
public class AliasStepHandler implements OperationStepHandler {

    private final AliasEntry aliasEntry;

    AliasStepHandler(final AliasEntry aliasEntry) {
        this.aliasEntry = aliasEntry;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String op = operation.require(OP).asString();
        final PathAddress addr = context.getCurrentAddress();

        WildcardReadResourceDescriptionAddressHack.attachAliasAddress(context, operation);

        final AliasContext aliasContext = AliasContext.create(operation, context);
        final PathAddress mapped = aliasEntry.convertToTargetAddress(addr, aliasContext);
        assert !addr.equals(mapped) : "Alias was not translated";

        final OperationStepHandler targetHandler = context.getRootResourceRegistration().getOperationHandler(mapped, op);
        if (targetHandler == null) {
            throw ControllerLogger.ROOT_LOGGER.aliasStepHandlerOperationNotFound(op, addr, mapped);
        }

        final ModelNode copy = operation.clone();
        copy.get(OP_ADDR).set(mapped.toModelNode());
        context.addStep(copy, targetHandler, Stage.MODEL, true);

        if (isTrimReadResourceDefinitionArray(op, addr, mapped)) {
            //If a 'singleton' alias address resolves to a wildcard target address, the r-r-d handler will be triggered
            //for the wildcard target address, which will return the results in an array. The caller however, should
            //see the results as a standard result for a singleton resource. So strip out the result here if that is the
            //case.
            //Note that this situation only applies to r-r-d, the other operations involve a specific resource existing
            //(or being added).
            //The tests uncommented in the commit introducing this demonstrate the issue.
            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    ModelNode result = context.getResult();
                    if (result.getType() == ModelType.LIST) {
                        List<ModelNode> list = result.asList();
                        if (list.size() == 1) {
                            ModelNode entry = list.get(0);
                            context.getResult().set(entry.get(RESULT));
                        }
                    }
                }
            });
        }
    }

    private boolean isTrimReadResourceDefinitionArray(String opName, PathAddress aliasAddr, PathAddress targetAddr) {
        if (!opName.equals(READ_RESOURCE_DESCRIPTION_OPERATION)) {
            return false;
        }
        if (aliasAddr.isMultiTarget()) {
            return false;
        }
        if (targetAddr.isMultiTarget()) {
            return true;
        }
        return false;
    }
}
