/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.model.jvm;

import java.util.Set;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * {@code OperationHandler} for the jvm resource add operation.
 *
 * @author Emanuel Muckenhuber
 */
final class JVMAddHandler extends AbstractAddStepHandler {
    public static final String OPERATION_NAME = ADD;

    private final AttributeDefinition[] attrs;
    JVMAddHandler(AttributeDefinition[] attrs) {
        this.attrs = attrs;
    }

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {

        super.populateModel(context, operation, resource);
        final ModelNode model = resource.getModel();

        // validate the jvm count is below maxOccurs, if maxOccurs is set to something other than the default
        // for server-config this is set to one, /host=*/jvm is unbounded. See the server boolean in {@link JvmResourceDefinition}.
        context.addStep(new OperationStepHandler() {
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                final Resource root = context.readResourceFromRoot(context.getCurrentAddress().getParent(), false);
                final int maxOccurs = context.getResourceRegistration().getMaxOccurs();
                Set<String> children = root.getChildrenNames(ModelDescriptionConstants.JVM);
                if (children.size() > maxOccurs) {
                    throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.exceedsMaxOccurs(ModelDescriptionConstants.JVM, maxOccurs));
                    // XXX fix
                }
            }
        }, OperationContext.Stage.RUNTIME);

        for (AttributeDefinition attr : attrs) {
            attr.validateAndSet(operation, model);
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
