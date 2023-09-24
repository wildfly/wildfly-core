/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.ELYTRON_CAPABILITY;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * An {@link OperationStepHandler} for removing a single service based on it's capability.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class TrivialCapabilityServiceRemoveHandler extends ServiceRemoveStepHandler implements ElytronOperationStepHandler {

    private final RuntimeCapability<?> firstCapability;
    private final Set<RuntimeCapability> allCapabilities;

    /**
     * Construct an {@link OperationStepHandler} for removing a single service based on it's capability.
     *
     * @param addOperation
     * @param runtimeCapabilities
     */
    TrivialCapabilityServiceRemoveHandler(AbstractAddStepHandler addOperation, RuntimeCapability<?> ... runtimeCapabilities) {
        super(addOperation);
        this.firstCapability = runtimeCapabilities[0];
        this.allCapabilities = new HashSet<>(Arrays.asList(runtimeCapabilities));
    }

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.recordCapabilitiesAndRequirements(context, operation, resource);
        final String pathValue = context.getCurrentAddressValue();
        for (RuntimeCapability r : allCapabilities) {
            context.deregisterCapabilityRequirement(ELYTRON_CAPABILITY, r.isDynamicallyNamed() ? r.getDynamicName(pathValue) : r.getName());
        }
    }

    /**
     * Create the name of the {@link Service} to be removed using the previously provided {@link RuntimeCapability} and the type
     * of the service.
     */
    @Override
    protected ServiceName serviceName(String name) {
        return firstCapability.fromBaseCapability(name).getCapabilityServiceName();
    }

    @Override
    protected boolean requiresRuntime(final OperationContext context) {
        return isServerOrHostController(context);
    }
}
