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

import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.as.server.operations.LaunchTypeHandler;

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

        resourceRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.LAUNCH_TYPE, new LaunchTypeHandler(ServerEnvironment.LaunchType.DOMAIN));
        resourceRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.SERVER_STATE, (context, operation) -> {
            // note this is inconsistent with the other values, should be lower case, preserved for now.
            context.getResult().set("STOPPED");
        });
        resourceRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.RUNTIME_CONFIGURATION_STATE,
                (context, operation) -> context.getResult().set(ClientConstants.CONTROLLER_PROCESS_STATE_STOPPED));
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration resourceRegistration) {
        // TODO also allow start,stop,restart,reload operations here
        // registerServerLifecycleOperations(resourceRegistration, serverInventory);

        // WFCORE-998 register composite here so addressing this resource as a step in a composite works
        resourceRegistration.registerOperationHandler(CompositeOperationHandler.INTERNAL_DEFINITION, CompositeOperationHandler.INSTANCE);
    }

}
