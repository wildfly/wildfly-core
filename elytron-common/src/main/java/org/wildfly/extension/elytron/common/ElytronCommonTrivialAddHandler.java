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

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.commonRequirements;

import java.util.Collections;
import java.util.HashSet;

import org.jboss.as.controller.AttributeDefinition;
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

/**
 * A trivial {@link OperationStepHandler} for adding a {@link Service} for a resource.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public abstract class ElytronCommonTrivialAddHandler<T> extends ElytronCommonBaseAddHandler {

    private final Class<?> extensionClass;
    private final RuntimeCapability<?> runtimeCapability;
    private final Mode initialMode;
    private final Mode adminOnlyInitialMode;

    public ElytronCommonTrivialAddHandler(Class<?> extensionClass, Class<T> serviceType, AttributeDefinition[] attributes, RuntimeCapability<?> runtimeCapability) {
        this(extensionClass, serviceType, Mode.ACTIVE, attributes, runtimeCapability);
    }

    public ElytronCommonTrivialAddHandler(Class<?> extensionClass, Class<T> serviceType, Mode initialMode, AttributeDefinition[] attributes, RuntimeCapability<?> runtimeCapability) {
        this(extensionClass, serviceType, initialMode, initialMode, attributes, runtimeCapability);
    }

    public ElytronCommonTrivialAddHandler(Class<?> extensionClass, Class<T> serviceType, Mode initialMode, Mode adminOnlyInitialMode, AttributeDefinition[] attributes, RuntimeCapability<?> runtimeCapability) {
        super(new HashSet<>(Collections.singletonList(checkNotNullParam("runtimeCapabilities", runtimeCapability))), attributes);
        this.extensionClass = extensionClass;
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
        installedForResource(commonRequirements(extensionClass, serviceBuilder, dependOnProperties(), dependOnProviderRegistration())
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

    protected abstract TrivialService.ValueSupplier<T> getValueSupplier(ServiceBuilder<T> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException;

}
