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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * A utility class for creating a {@link ServiceName} for and handling injections.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class ServiceUtil<T> {

    private final RuntimeCapability<?> runtimeCapability;
    private final String key;
    private final Class<T> clazz;

    private ServiceUtil(RuntimeCapability<?> runtimeCapability, String key, Class<T> injectionClass) {
        this.runtimeCapability = runtimeCapability;
        this.key = key;
        clazz = injectionClass;
    }

    /**
     * Construct the {@link ServiceName} for a {@link Service} given it's simple name.
     *
     * @param name - the simple name of the service.
     * @return The fully qualified {@link ServiceName} of the service.
     */
    ServiceName serviceName(final String name) {
        RuntimeCapability<?> dynamicCapability = runtimeCapability.fromBaseCapability(name);
        return dynamicCapability.getCapabilityServiceName(clazz);
    }

    /**
     * From a given operation extract the address of the operation, identify the simple name of the {@link Service} being
     * referenced and convert it into a {@link ServiceName} for that {@link Service}.
     *
     * @param operation - the operation to extract the simple name from.
     * @return The fully qualified {@link ServiceName} of the service.
     */
    ServiceName serviceName(final ModelNode operation) {
        String name = null;
        PathAddress pa = PathAddress.pathAddress(operation.require(OP_ADDR));
        for (int i = pa.size() - 1; i > 0; i--) {
            PathElement pe = pa.getElement(i);
            if (key.equals(pe.getKey())) {
                name = pe.getValue();
                break;
            }
        }

        if (name == null) {
            throw ROOT_LOGGER.operationAddressMissingKey(key);
        }

        return serviceName(name);
    }

    /**
     * Using the supplied {@link Injector} add a dependency on the {@link Service} identified by the supplied name.
     *
     * @param sb - the {@link ServiceBuilder} to use for the injection.
     * @param injector - the {@link Injector} to inject into.
     * @param name - the simple name of the service to inject.
     * @return The {@link ServiceBuilder} passed in to allow method chaining.
     */
    ServiceBuilder<?> addInjection(ServiceBuilder<?> sb, Injector<T> injector, String name) {
        return addInjection(sb, injector, serviceName(name));
    }

    ServiceBuilder<?> addInjection(ServiceBuilder<?> sb, Injector<T> injector, ServiceName serviceName) {
        sb.addDependency(serviceName, clazz, injector);
        return sb;
    }

    public static <T> ServiceUtil<T> newInstance(RuntimeCapability<?> runtimeCapability, String key, Class<T> injectionClass) {
        return new ServiceUtil<T>(runtimeCapability, key, injectionClass);
    }

}
