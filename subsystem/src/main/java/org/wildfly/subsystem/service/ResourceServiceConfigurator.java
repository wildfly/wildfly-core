/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Configures a service for a given resource.
 * @author Paul Ferraro
 */
public interface ResourceServiceConfigurator {

    /**
     * Configures a service using the specified operation context and model.
     * @param context an operation context, used to resolve capabilities and expressions
     * @param model the resource model
     * @return a service installer
     * @throws OperationFailedException if there was a failure reading the model or resolving expressions/capabilities
     */
    ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException;

    /**
     * Returns a composite {@link ResourceServiceConfigurator} that configures the specified service configurators.
     * @param configurators a variable number of configurators
     * @return a composite service configurator
     */
    static ResourceServiceConfigurator combine(ResourceServiceConfigurator... configurators) {
        return combine(List.of(configurators));
    }

    /**
     * Returns a composite {@link ResourceServiceConfigurator} that configures the specified service configurators.
     * @param configurators a collection of configurators
     * @return a composite configurator
     */
    static ResourceServiceConfigurator combine(Collection<? extends ResourceServiceConfigurator> configurators) {
        return new ResourceServiceConfigurator() {
            @Override
            public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
                List<ResourceServiceInstaller> installers = new ArrayList<>(configurators.size());
                for (ResourceServiceConfigurator configurator : configurators) {
                    installers.add(configurator.configure(context, model));
                }
                return ResourceServiceInstaller.combine(installers);
            }
        };
    }
}
