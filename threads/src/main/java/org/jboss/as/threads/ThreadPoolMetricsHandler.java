/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.List;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Base class for operation step handlers that expose thread pool resource metrics.
 *
 * @author Alexey Loubyansky
 */
public abstract class ThreadPoolMetricsHandler extends AbstractRuntimeOnlyHandler {

    private final List<AttributeDefinition> metrics;
    private final ServiceName serviceNameBase;
    private final RuntimeCapability capability;

    protected ThreadPoolMetricsHandler(final List<AttributeDefinition> metrics, final RuntimeCapability capability, final ServiceName serviceNameBase) {
        this.metrics = metrics;
        this.serviceNameBase = serviceNameBase;
        this.capability = capability;
    }

    public void registerAttributes(final ManagementResourceRegistration registration) {
        for (AttributeDefinition metric : metrics) {
            registration.registerMetric(metric, this);
        }
    }

    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String attributeName = operation.require(ModelDescriptionConstants.NAME).asString();
        if (context.getRunningMode() == RunningMode.NORMAL) {
            ServiceController<?> serviceController = getService(context, operation);
            final Service<?> service = serviceController.getService();
            setResult(context, attributeName, service);
        }

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    protected abstract void setResult(OperationContext context, String attributeName, Service<?> service) throws OperationFailedException;

    protected ServiceController<?> getService(final OperationContext context, final ModelNode operation)
            throws OperationFailedException {
        final String name = Util.getNameFromAddress(operation.require(OP_ADDR));
        ServiceName serviceName = null;
        ServiceController<?> controller = null;
        if(capability != null) {
            serviceName = capability.getCapabilityServiceName(context.getCurrentAddress());
            controller = context.getServiceRegistry(false).getService(serviceName);
            if(controller != null) {
                return controller;
            }
        }
        if (serviceNameBase != null) {
            serviceName = serviceNameBase.append(name);
            controller = context.getServiceRegistry(false).getService(serviceName);
        }
        if (controller == null) {
            throw ThreadsLogger.ROOT_LOGGER.threadPoolServiceNotFoundForMetrics(serviceName);
        }
        return controller;
    }

}
