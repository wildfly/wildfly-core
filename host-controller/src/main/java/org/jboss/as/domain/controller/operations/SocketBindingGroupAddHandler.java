/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the domain socket-binding-group resource's add operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 *
 */
public class SocketBindingGroupAddHandler extends ModelOnlyAddStepHandler {

    public static final SocketBindingGroupAddHandler INSTANCE = new SocketBindingGroupAddHandler();

    private SocketBindingGroupAddHandler() {
        super(SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE, SocketBindingGroupResourceDefinition.INCLUDES);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.populateModel(context, operation, resource);

        //  We need to store the address value in the 'name' instead of using
        // ReadResourceNameOperationStepHandler to avoid picky legacy controller
        // model comparison failures
        resource.getModel().get(NAME).set(context.getCurrentAddressValue());

        DomainModelIncludesValidator.addValidationStep(context, operation);
    }
}
