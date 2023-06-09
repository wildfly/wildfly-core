/*
 * Copyright 2021 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.common;

import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.commonRequirements;

import java.util.function.Consumer;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController.Mode;
import org.wildfly.common.function.ExceptionFunction;

/**
 * An add handler which makes use of a {@code Doohickey} to coordinate making a resource available
 * both as an MSC service and as a runtime API.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public abstract class ElytronCommonDoohickeyAddHandler<T> extends ElytronCommonBaseAddHandler {

    private final Class<?> extensionClass;
    private final RuntimeCapability<?> runtimeCapability;
    private final String apiCapabilityName;

    public ElytronCommonDoohickeyAddHandler(Class<?> extensionClass, RuntimeCapability<?> runtimeCapability, AttributeDefinition[] configAttributes, String apiCapabilityName) {
        super(runtimeCapability, configAttributes);
        this.extensionClass = extensionClass;
        this.runtimeCapability =  runtimeCapability;
        this.apiCapabilityName = apiCapabilityName;
    }

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource)
            throws OperationFailedException {
        super.recordCapabilitiesAndRequirements(context, operation, resource);

        if (requiresRuntime(context)) {
            // Just add one capability to allow access to the CredentialStore using the runtime API.
            ElytronDoohickey<T> elytronDoohickey = createDoohickey(context.getCurrentAddress());

            context.registerCapability(RuntimeCapability.Builder
                    .<ExceptionFunction<OperationContext, T, OperationFailedException>> of(apiCapabilityName, true,
                            elytronDoohickey)
                    .build().fromBaseCapability(context.getCurrentAddressValue()));
        }
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final String name = context.getCurrentAddressValue();
        ExceptionFunction<OperationContext, T, OperationFailedException> runtimeApi = context.getCapabilityRuntimeAPI(apiCapabilityName,
                name, ExceptionFunction.class);

        ElytronDoohickey<T> doohickey = (ElytronDoohickey) runtimeApi;
        doohickey.resolveRuntime(context); // Must call parent 'resolveRuntime' as it handles the synchronization.

        CapabilityServiceBuilder<?> serviceBuilder = context.getCapabilityServiceTarget().addCapability(runtimeCapability);

        Consumer<T> valueConsumer = serviceBuilder.provides(runtimeCapability);
        doohickey.prepareService(context, serviceBuilder);

        final TrivialService<T> trivialService = new TrivialService<>(doohickey::get, valueConsumer);

        commonRequirements(extensionClass, serviceBuilder.setInitialMode(Mode.ACTIVE).setInstance(trivialService), true, dependOnProviderRegistration()).install();
    }

    protected boolean dependOnProviderRegistration() {
        return true;
    }

    protected Mode getInitialMode() {
        return Mode.ACTIVE;
    }

    protected abstract ElytronDoohickey<T> createDoohickey(final PathAddress resourceAddress);

}