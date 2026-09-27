/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
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

    public DoohickeyAddHandler(RuntimeCapability<?> runtimeCapability, String apiCapabilityName) {
        super(runtimeCapability);
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
    protected Resource createResource(final OperationContext context) {
        return createResourceForAdd(context);
    }

    /**
     * Hook for subclasses that need to supply a custom {@link Resource} (e.g. to hold a service controller reference).
     * The default implementation delegates to the standard {@link OperationContext#createResource}.
     */
    protected Resource createResourceForAdd(final OperationContext context) {
        return context.createResource(PathAddress.EMPTY_ADDRESS);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final String name = context.getCurrentAddressValue();
        ExceptionFunction<OperationContext, T, OperationFailedException> runtimeApi = context.getCapabilityRuntimeAPI(apiCapabilityName,
                name, ExceptionFunction.class);

        ElytronDoohickey<T> doohickey = (ElytronDoohickey) runtimeApi;
        doohickey.resolveRuntime(context); // Must call parent 'resolveRuntime' as it handles the synchronization.

        CapabilityServiceBuilder<?> serviceBuilder = context.getCapabilityServiceTarget().addCapability(runtimeCapability);

        doohickey.prepareService(context, serviceBuilder);

        final TrivialService<T> trivialService = new TrivialService<>();
        trivialService.setValueSupplier(doohickey.asValueSupplier());

        @SuppressWarnings("unchecked")
        ServiceController<T> serviceController = (ServiceController<T>) commonDependencies(
                serviceBuilder.setInitialMode(getInitialMode()).setInstance(trivialService), true, dependOnProviderRegistration()).install();
        installedForResource(serviceController, resource);
    }

    /**
     * Called after the service controller has been installed. Subclasses may override to store the controller
     * reference on the resource (e.g. for operations that need to interact with the running service).
     */
    @SuppressWarnings("unused")
    protected void installedForResource(ServiceController<T> serviceController, Resource resource) {
    }

    protected boolean dependOnProviderRegistration() {
        return true;
    }

    protected Mode getInitialMode() {
        return Mode.ACTIVE;
    }

    protected abstract ElytronDoohickey<T> createDoohickey(final PathAddress resourceAddress);

}