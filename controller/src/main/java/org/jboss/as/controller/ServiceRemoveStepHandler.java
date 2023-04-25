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

package org.jboss.as.controller;

import java.util.Set;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Abstract remove step handler that simply removes a service. If the operation is rolled
 * back it delegates the rollback to the corresponding add operations
 * {@link AbstractAddStepHandler#performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}
 * method.
 *
 * @author Stuart Douglas
 */
public class ServiceRemoveStepHandler extends AbstractRemoveStepHandler {

    private final ServiceName baseServiceName;
    private final AbstractAddStepHandler addOperation;

    /**
     * Creates a {@code ServiceRemoveStepHandler}.
     * @param baseServiceName base name to remove. Cannot be {@code null}
     * @param addOperation the add operation to use to rollback service removal. Cannot be {@code null}
     */
    public ServiceRemoveStepHandler(final ServiceName baseServiceName, final AbstractAddStepHandler addOperation) {
        this.baseServiceName = baseServiceName;
        this.addOperation = addOperation;
    }

    /**
     * Creates a {@code ServiceRemoveStepHandler}.
     * @param addOperation the add operation to use to rollback service removal. Cannot be {@code null}
     */
    public ServiceRemoveStepHandler(final AbstractAddStepHandler addOperation) {
        this(null, addOperation);
    }

    /**
     * If the {@link OperationContext#isResourceServiceRestartAllowed() context allows resource removal},
     * removes services; otherwise puts the process in reload-required state. The following services are
     * removed:
     * <ul>
     *     <li>The service named by the value returned from {@link #serviceName(String, PathAddress)}, if there is one</li>
     *     <li>The service names associated with any {@code unavailableCapabilities}
     *         passed to the constructor.</li>
     * </ul>
     *
     * {@inheritDoc}
     */
    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        if (context.isResourceServiceRestartAllowed()) {

            final PathAddress address = context.getCurrentAddress();
            final String name = address.getLastElement().getValue();

            ServiceName nonCapabilityServiceName = serviceName(name, address);
            if (nonCapabilityServiceName != null) {
                context.removeService(serviceName(name, address));
            }

            Set<RuntimeCapability> capabilitySet = context.getResourceRegistration().getCapabilities();

            for (RuntimeCapability<?> capability : capabilitySet) {
                if (capability.getCapabilityServiceValueType() != null) {
                    context.removeService(capability.getCapabilityServiceName(address));
                }
            }
        } else {
            context.reloadRequired();
        }
    }

    /**
     * Is a runtime step required?  By default his method delegates to the supplied {@link AbstractAddStepHandler}, if
     * the add handler required a runtime step then most likely the remove step handler will also require one.
     *
     * @see org.jboss.as.controller.AbstractRemoveStepHandler#requiresRuntime(org.jboss.as.controller.OperationContext)
     */
    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return addOperation.requiresRuntime(context);
    }

    /**
     * The service name to be removed. Can be overridden for unusual service naming patterns
     * @param name The name of the resource being removed
     * @param address The address of the resource being removed
     * @return The service name to remove. May return {@code null} if only removal based on {@code unavailableCapabilities}
     *         passed to the constructor are to be performed
     */
    protected ServiceName serviceName(String name, PathAddress address) {
        return serviceName(name);
    }

    /**
     * The service name to be removed. Can be overridden for unusual service naming patterns
     * @param name The name of the resource being removed
     * @return The service name to remove. May return {@code null} if only removal based on {@code unavailableCapabilities}
     *         passed to the constructor are to be performed
     */
    protected ServiceName serviceName(final String name) {
        return baseServiceName != null ? baseServiceName.append(name) : null;
    }

    /**
     * If the {@link OperationContext#isResourceServiceRestartAllowed() context allows resource removal},
     * attempts to restore services by invoking the {@code performRuntime} method on the @{code addOperation}
     * handler passed to the constructor; otherwise puts the process in reload-required state.
     *
     * {@inheritDoc}
     */
    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        if (context.isResourceServiceRestartAllowed()) {
            addOperation.performRuntime(context, operation, model);
        } else {
            context.revertReloadRequired();
        }
    }
}
