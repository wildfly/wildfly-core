/*
 * Copyright 2023 Red Hat, Inc.
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
