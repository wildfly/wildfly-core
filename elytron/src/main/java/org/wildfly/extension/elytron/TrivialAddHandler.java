/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;

/**
 * A trivial {@link OperationStepHandler} for adding a {@link Service} for a resource.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class TrivialAddHandler<T> extends BaseAddHandler {

    private final RuntimeCapability<?> runtimeCapability;
    private final Mode initialMode;
    private final Mode adminOnlyInitialMode;

    TrivialAddHandler(Class<T> serviceType, RuntimeCapability<?> runtimeCapability) {
        this(serviceType, Mode.ACTIVE, runtimeCapability);
    }

    TrivialAddHandler(Class<T> serviceType, Mode initialMode, RuntimeCapability<?> runtimeCapability) {
        this(serviceType, initialMode, initialMode, runtimeCapability);
    }

    TrivialAddHandler(Class<T> serviceType, Mode initialMode, Mode adminOnlyInitialMode, RuntimeCapability<?> runtimeCapability) {
        super(Set.of(checkNotNullParam("runtimeCapabilities", runtimeCapability)));
        this.runtimeCapability = runtimeCapability;
        checkNotNullParam("serviceType", serviceType);
        this.initialMode = checkNotNullParam("initialMode", initialMode);
        this.adminOnlyInitialMode = checkNotNullParam("adminOnlyInitialMode", adminOnlyInitialMode);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final void performRuntime(OperationContext context, ModelNode operation, Resource resource)
            throws OperationFailedException {
        TrivialService<T> trivialService = new TrivialService<T>();

        ServiceBuilder<T> serviceBuilder = (ServiceBuilder<T>)context.getCapabilityServiceTarget().addCapability(runtimeCapability);
        serviceBuilder.setInstance(trivialService);

        trivialService.setValueSupplier(getValueSupplier(serviceBuilder, context, resource.getModel()));
        installedForResource(commonDependencies(serviceBuilder, dependOnProperties(), dependOnProviderRegistration())
                .setInitialMode(context.getRunningMode() == RunningMode.ADMIN_ONLY ? adminOnlyInitialMode : initialMode)
                .install(), resource);
    }

    protected boolean dependOnProperties() {
        return true;
    }

    protected boolean dependOnProviderRegistration() {
        return true;
    }

    protected void installedForResource(ServiceController<T> serviceController, Resource resource) {}

    protected abstract ValueSupplier<T> getValueSupplier(ServiceBuilder<T> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException;

}
