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
package org.jboss.as.server.services.net;

import static org.jboss.as.server.services.net.SocketBindingResourceDefinition.SOCKET_BINDING_CAPABILITY;

import java.net.UnknownHostException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handler for the server and host model socket-binding resource's remove operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BindingRemoveHandler extends SocketBindingRemoveHandler {

    public static final BindingRemoveHandler INSTANCE = new BindingRemoveHandler();

    private BindingRemoveHandler() {
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        String name = context.getCurrentAddressValue();
        ServiceName svcName = SOCKET_BINDING_CAPABILITY.getCapabilityServiceName(name);
        ServiceRegistry registry = context.getServiceRegistry(true);
        ServiceController<?> controller = registry.getService(svcName);
        ServiceController.State state = controller == null ? null : controller.getState();
        if (!context.isResourceServiceRestartAllowed() || (state == ServiceController.State.UP)) {
            context.reloadRequired();
        } else {
            context.removeService(svcName);
        }
    }

    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        String name = context.getCurrentAddressValue();
        ServiceName svcName = SOCKET_BINDING_CAPABILITY.getCapabilityServiceName(name);
        ServiceRegistry registry = context.getServiceRegistry(true);
        ServiceController<?> controller = registry.getService(svcName);
        if (controller != null) {
            // We didn't remove it, we just set reloadRequired
            context.revertReloadRequired();
        } else {
            try {
                BindingAddHandler.installBindingService(context, model, name);
            }catch (UnknownHostException e) {
                throw new OperationFailedException(e.toString());
            }
        }
    }
}
