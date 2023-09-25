/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

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
 */
abstract class DoohickeyAddHandler<T> extends BaseAddHandler {

    private final RuntimeCapability<?> runtimeCapability;
    private final String apiCapabilityName;

    public DoohickeyAddHandler(RuntimeCapability<?> runtimeCapability, AttributeDefinition[] configAttributes, String apiCapabilityName) {
        super(runtimeCapability, configAttributes);
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

        commonDependencies(serviceBuilder.setInitialMode(Mode.ACTIVE).setInstance(trivialService), true, dependOnProviderRegistration()).install();
    }

    protected boolean dependOnProviderRegistration() {
        return true;
    }

    protected Mode getInitialMode() {
        return Mode.ACTIVE;
    }

    protected abstract ElytronDoohickey<T> createDoohickey(final PathAddress resourceAddress);

}