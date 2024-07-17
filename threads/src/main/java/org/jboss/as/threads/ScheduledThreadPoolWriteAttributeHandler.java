/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;


import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;


/**
 * Handles attribute writes for a scheduled thread pool.
 *
 * @author Alexey Loubyansky
 */
public class ScheduledThreadPoolWriteAttributeHandler extends ThreadsWriteAttributeOperationHandler {

    private final ServiceName serviceNameBase;
    private final RuntimeCapability capability;

    public ScheduledThreadPoolWriteAttributeHandler(final RuntimeCapability capability, ServiceName serviceNameBase) {
        super(ScheduledThreadPoolAdd.ATTRIBUTES);
        this.serviceNameBase = serviceNameBase;
        this.capability = capability;
    }

    @Override
    protected void applyOperation(final OperationContext context, ModelNode operation, String attributeName,
                                  ServiceController<?> service, boolean forRollback) {
        if (!forRollback) {
            // Programming bug. Throw a RuntimeException, not OFE, as this is not a client error
            throw ThreadsLogger.ROOT_LOGGER.unsupportedScheduledThreadPoolAttribute(attributeName);
        }
    }

    @Override
    protected ServiceController<?> getService(final OperationContext context, final ModelNode model) throws OperationFailedException {
        final String name = context.getCurrentAddressValue();
        ServiceName serviceName = null;
        ServiceController<?> controller = null;
        if(capability != null) {
            serviceName = capability.getCapabilityServiceName(context.getCurrentAddress());
            controller = context.getServiceRegistry(true).getService(serviceName);
            if(controller != null) {
                return controller;
            }
        }
        if (serviceNameBase != null) {
            serviceName = serviceNameBase.append(name);
            controller = context.getServiceRegistry(true).getService(serviceName);
        }
        if(controller == null) {
            throw ThreadsLogger.ROOT_LOGGER.scheduledThreadPoolServiceNotFound(serviceName);
        }
        return controller;
    }
}
