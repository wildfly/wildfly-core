/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;

/**
 * A {@link org.jboss.msc.service.ServiceBuilder} that supports capability requirements.
 * @param <T> an ignored service value type
 * @author Paul Ferraro
 */
public interface RequirementServiceBuilder<T> extends ServiceBuilder<T> {

    @Override
    RequirementServiceBuilder<T> setInitialMode(ServiceController.Mode mode);

    @Override
    RequirementServiceBuilder<T> setInstance(Service service);

    @Override
    RequirementServiceBuilder<T> addListener(LifecycleListener listener);

    /**
     * Registers a requirement on the specified capability.
     * @param capabilityName name of capability requirement
     * @param dependencyType the class of the value of the dependency
     * @param referenceNames dynamic part(s) of capability name, only useful when using dynamic named capabilities
     * @param <V> the type of the value of the dependency
     * @return a reference to the required dependency
     */
    <V> Supplier<V> requiresCapability(String capabilityName, Class<V> dependencyType, String... referenceNames);
}
