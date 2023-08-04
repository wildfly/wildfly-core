/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.subsystem.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.transform.TransformerOperationAttachment;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TransformerAttachmentGrabber implements OperationStepHandler {
    private static final AttributeDefinition VALUE =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.VALUE, ModelType.OBJECT).build();

    static final OperationDefinition DESC =
            new SimpleOperationDefinitionBuilder("execute-grab-attachment-and-transform", NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setParameters(VALUE)
                    .build();

    private static TransformerOperationAttachment attachment;

    TransformerAttachmentGrabber() {
    }

    static void clear() {
        attachment = null;
    }

    public static TransformerOperationAttachment getAttachment() {
        return attachment;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        if (attachment != null) {
            Assert.fail("Attachment is already set");
        }
        ModelNode realOp = operation.require(VALUE.getName());
        String opName = realOp.require(OP).asString();
        PathAddress opAddr = PathAddress.pathAddress(realOp.require(OP_ADDR));
        OperationStepHandler handler = context.getRootResourceRegistration().getOperationHandler(opAddr, opName);
        context.addStep(realOp, handler, OperationContext.Stage.MODEL);

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                attachment = context.getAttachment(TransformerOperationAttachment.KEY);
            }
        }, OperationContext.Stage.RUNTIME); //Use RUNTIME here to make sure that this comes at the end (the collection ops add another step)
    }
}
