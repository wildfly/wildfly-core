/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronCommonCapabilities.ELYTRON_CAPABILITY;

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
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
class ElytronCommonBaseAddHandler extends AbstractAddStepHandler implements ElytronOperationStepHandler {

    private final Set<RuntimeCapability> runtimeCapabilities;

    /**
     * Constructor of the add handler that takes an array of {@link AttributeDefinition}.
     *
     * @param attributes  the {@link AttributeDefinition} instances associated with this resource.
     */
    ElytronCommonBaseAddHandler(AttributeDefinition... attributes) {
        super(attributes);
        this.runtimeCapabilities = Collections.emptySet();
    }

    /**
     * Constructor of the add handler that takes a {@link RuntimeCapability} and array of {@link AttributeDefinition}.
     *
     * @param runtimeCapability the {@link RuntimeCapability} that will be provided at runtime.
     * @param attributes the {@link AttributeDefinition} instances associated with this resource.
     */
    ElytronCommonBaseAddHandler(RuntimeCapability<?> runtimeCapability, AttributeDefinition... attributes) {
        super(attributes);
        this.runtimeCapabilities = Collections.singleton(runtimeCapability);
    }



    /**
     * Constructor of the add handler that takes a {@link Set} of {@link RuntimeCapability} and array of {@link AttributeDefinition}.
     *
     * @param capabilities a {@link Set} of capabilitiies that will be added.
     * @param attributes the {@link AttributeDefinition} instances associated with this resource.
     */
    ElytronCommonBaseAddHandler(Set<RuntimeCapability> capabilities, AttributeDefinition... attributes) {
        super(attributes);
        this.runtimeCapabilities = capabilities;
    }



    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.recordCapabilitiesAndRequirements(context, operation, resource);
        final String pathValue = context.getCurrentAddressValue();
        for (RuntimeCapability r : runtimeCapabilities) {
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
