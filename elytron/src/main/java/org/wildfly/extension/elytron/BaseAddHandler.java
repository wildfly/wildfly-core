/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.ELYTRON_CAPABILITY;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * An extension of {@link AbstractAddStepHandler} to ensure all Elytron runtime operations are performed in the required server
 * states.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class BaseAddHandler extends AbstractAddStepHandler implements ElytronOperationStepHandler {

    private final Set<RuntimeCapability<?>> runtimeCapabilities;

    /**
     * Constructor of the add handler that takes an array of {@link AttributeDefinition}.
     *
     * @param attributes  the {@link AttributeDefinition} instances associated with this resource.
     */
    BaseAddHandler() {
        this.runtimeCapabilities = Collections.emptySet();
    }

    /**
     * Constructor of the add handler that takes a {@link RuntimeCapability} and array of {@link AttributeDefinition}.
     *
     * @param runtimeCapability the {@link RuntimeCapability} that will be provided at runtime.
     * @param attributes the {@link AttributeDefinition} instances associated with this resource.
     */
    BaseAddHandler(RuntimeCapability<?> runtimeCapability) {
        this.runtimeCapabilities = Collections.singleton(runtimeCapability);
    }



    /**
     * Constructor of the add handler that takes a {@link Set} of {@link RuntimeCapability} and array of {@link AttributeDefinition}.
     *
     * @param capabilities a {@link Set} of capabilitiies that will be added.
     * @param attributes the {@link AttributeDefinition} instances associated with this resource.
     */
    BaseAddHandler(Set<RuntimeCapability<?>> capabilities) {
        this.runtimeCapabilities = capabilities;
    }



    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.recordCapabilitiesAndRequirements(context, operation, resource);
        final String pathValue = context.getCurrentAddressValue();
        for (RuntimeCapability<?> r : runtimeCapabilities) {
            context.registerAdditionalCapabilityRequirement(ELYTRON_CAPABILITY, r.isDynamicallyNamed() ? r.getDynamicName(pathValue) : r.getName(), null);
        }
    }

    /**
     * Ensures runtime operations are performed in the usual modes and also for an admin only server.
     *
     * @return Returns {@code true} in the existing situations and also for admin-only mode of a normal server.
     * @see org.jboss.as.controller.AbstractAddStepHandler#requiresRuntime(org.jboss.as.controller.OperationContext)
     */
    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return isServerOrHostController(context);
    }

}
