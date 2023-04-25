/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class NativeManagementRemoveHandler extends ReloadRequiredRemoveStepHandler {

    public static final NativeManagementRemoveHandler INSTANCE = new NativeManagementRemoveHandler();

    public NativeManagementRemoveHandler() {
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return (context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER);
    }

    @Override
    protected void performRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final PathAddress httpAddress = context.getCurrentAddress().getParent().append(PathElement.pathElement(MANAGEMENT_INTERFACE, HTTP_INTERFACE));
        context.addStep((OperationContext opContext, ModelNode op) -> {
            ManagementRemotingServices.isManagementResourceRemoveable(opContext, httpAddress);
        }, OperationContext.Stage.MODEL, false);
        super.performRemove(context, operation, model);
    }
}