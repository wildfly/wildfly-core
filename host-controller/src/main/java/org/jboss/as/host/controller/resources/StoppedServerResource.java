/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;

import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 * {@code ResourceDescription} describing a stopped server instance.
 *
 * @author Emanuel Muckenhuber
 */
public class StoppedServerResource extends SimpleResourceDefinition {

    private static final PathElement SERVER = PathElement.pathElement(ModelDescriptionConstants.RUNNING_SERVER);

    public StoppedServerResource() {
        super(SERVER, HostResolver.getResolver(ModelDescriptionConstants.RUNNING_SERVER, false));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {

        resourceRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.LAUNCH_TYPE, (context,operation) -> {
            readResourceServerConfig(context, operation);
            context.getResult().set(ServerEnvironment.LaunchType.DOMAIN.toString());
        });

        resourceRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.SERVER_STATE, (context, operation) -> {
            readResourceServerConfig(context, operation);
            // note this is inconsistent with the other values, should be lower case, preserved for now.
            context.getResult().set("STOPPED");
        });

        resourceRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.RUNTIME_CONFIGURATION_STATE,
                (context, operation) -> {
                    readResourceServerConfig(context, operation);
                    context.getResult().set(ClientConstants.CONTROLLER_PROCESS_STATE_STOPPED);
                    }
                );
    }

    // https://issues.jboss.org/browse/WFCORE-3338 read server-config to test name of the server in operation is present
    private void readResourceServerConfig(OperationContext context, ModelNode operation) {
        final PathAddress address = context.getCurrentAddress();
        final String hostName = address.getElement(0).getValue();
        final PathElement element = address.getLastElement();
        final String serverName = element.getValue();

        final ModelNode addr = new ModelNode();
        addr.add(HOST, hostName);
        addr.add(SERVER_CONFIG, serverName);
        context.readResourceFromRoot(PathAddress.pathAddress(addr), false);
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration resourceRegistration) {
        // TODO also allow start,stop,restart,reload operations here
        // registerServerLifecycleOperations(resourceRegistration, serverInventory);

        // WFCORE-998 register composite here so addressing this resource as a step in a composite works
        resourceRegistration.registerOperationHandler(CompositeOperationHandler.INTERNAL_DEFINITION, CompositeOperationHandler.INSTANCE);
    }
}
