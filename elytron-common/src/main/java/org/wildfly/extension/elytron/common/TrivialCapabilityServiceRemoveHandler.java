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
package org.wildfly.extension.elytron.common;

import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.ELYTRON_CAPABILITY;

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
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;

/**
 * An {@link OperationStepHandler} for removing a single service based on it's capability.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class TrivialCapabilityServiceRemoveHandler extends ServiceRemoveStepHandler implements ElytronOperationStepHandler {

    private final RuntimeCapability<?> firstCapability;
    private final Set<RuntimeCapability> allCapabilities;

    /**
     * Construct an {@link OperationStepHandler} for removing a single service based on it's capability.
     *
     * @param addOperation
     * @param runtimeCapabilities
     */
    public TrivialCapabilityServiceRemoveHandler(AbstractAddStepHandler addOperation, RuntimeCapability<?>... runtimeCapabilities) {
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
