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
package org.jboss.as.server.services.net;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller._private.OperationFailedRuntimeException;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.interfaces.ParsedInterfaceCriteria;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;

/**
 * Handler for adding a fully specified interface.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SpecifiedInterfaceAddHandler extends InterfaceAddHandler {

    public static final SpecifiedInterfaceAddHandler INSTANCE = new SpecifiedInterfaceAddHandler();

    protected SpecifiedInterfaceAddHandler() {
        super(true);
    }

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.recordCapabilitiesAndRequirements(context, operation, resource);

        // Directly register the capability in addition to relying on the MRR,
        // as in some subsystem test stuff (ControllerInitializer.initializeSocketBindingsModel)
        // due to restrictions for legacy controllers the MRR can't record the cap.
        // This will fail if the MRR-driven registration was successful, so ignore such a failure
        RuntimeCapability<?> cap = InterfaceResourceDefinition.INTERFACE_CAPABILITY.fromBaseCapability(context.getCurrentAddress());
        try {
            context.registerCapability(cap);
        } catch (OperationFailedRuntimeException ofre) {
            // ignore
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return context.getProcessType().isServer();
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model, String name, ParsedInterfaceCriteria criteria) {
        context.getCapabilityServiceTarget().addCapability(InterfaceResourceDefinition.INTERFACE_CAPABILITY.fromBaseCapability(name))
            .setInstance(createInterfaceService(name, criteria))
            .addAliases(NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append(name))
            .setInitialMode(ServiceController.Mode.ON_DEMAND)
            .install();
    }

    /**
     * Create a {@link NetworkInterfaceService}.
     *
     * @return the interface service
     */
    private static Service<NetworkInterfaceBinding> createInterfaceService(String name, ParsedInterfaceCriteria criteria) {
        return NetworkInterfaceService.create(name, criteria);
    }


}
