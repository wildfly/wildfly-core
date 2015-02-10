/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.domain.controller.resources.DomainResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Emanuel Muckenhuber
 */
public class ProfileCloneHandler implements OperationStepHandler {

    public static final ProfileCloneHandler INSTANCE = new ProfileCloneHandler();
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("clone", DomainResolver.getResolver(PROFILE, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .addParameter(SimpleAttributeDefinitionBuilder.create("to-profile", ModelType.STRING).build())
            // .setPrivateEntry()
            .build();


    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        final String profileName = PathAddress.pathAddress(operation.require(OP_ADDR)).getLastElement().getValue();
        final String newProfile = operation.require("to-profile").asString();
        final String operationName = GenericModelDescribeOperationHandler.DEFINITION.getName();
        final PathAddress address = PathAddress.pathAddress(PathElement.pathElement(PROFILE, profileName));

        final ModelNode describeOp = new ModelNode();
        describeOp.get(OP).set(operationName);
        describeOp.get(OP_ADDR).set(address.toModelNode());

        final ModelNode result = new ModelNode();

        final OperationStepHandler handler = context.getRootResourceRegistration().getOperationHandler(address, operationName);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                final PathAddress newPA = PathAddress.pathAddress(PROFILE, newProfile);
                final List<ModelNode> operations = new ArrayList<>(result.get(RESULT).asList());
                Collections.reverse(operations);
                for (final ModelNode op : operations) {
                    final PathAddress a = newPA.append(PathAddress.pathAddress(op.require(OP_ADDR)).subAddress(1));
                    op.get(OP_ADDR).set(a.toModelNode());

                    final OperationStepHandler h = context.getRootResourceRegistration().getOperationHandler(a, op.get(OP).asString());
                    context.addStep(op, h, OperationContext.Stage.MODEL, true);
                }
            }
        }, OperationContext.Stage.MODEL, true);

        context.addStep(result, describeOp, handler, OperationContext.Stage.MODEL, true);
    }


}
