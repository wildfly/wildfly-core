/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * A builder for an individual service in a {@code CapabilityServiceTarget}.
 * Create an instance via the {@link CapabilityServiceTarget#addCapability(RuntimeCapability, Service)},
 * {@link #addCapabilityRequirement(String, Class, Injector)} or other methods.
 * Builder also add supports to add capability requirement for service injection via {@link #addCapabilityRequirement(String, Class, Injector)}
 *
 * @param <T> the service type
 * @author Tomaz Cerar (c) 2017 Red Hat Inc.
 */
public interface CapabilityServiceBuilder<T> extends ServiceBuilder<T> {
    /**
     * Adds capability requirement with injection to service which we are building
     *
     * @param capabilityName name of capability requirement
     * @param type           the class of the value of the dependency
     * @param target         the injector into which the dependency should be stored
     * @param <I>            the type of the value of the dependency
     * @param referenceNames dynamic part(s) of capability name, only useful when using dynamic named capabilities
     * @return this builder
     */
    <I> CapabilityServiceBuilder<T> addCapabilityRequirement(String capabilityName, Class<I> type, Injector<I> target, String... referenceNames);

    /**
     * Adds capability requirement with injection to capability which we are building
     *
     * @param capabilityName name of capability requirement
     * @param type           the class of the value of the dependency
     * @param target         the injector into which the dependency should be stored
     * @param <I>            the type of the value of the dependency
     * @return this builder
     */
    <I> CapabilityServiceBuilder<T> addCapabilityRequirement(String capabilityName, Class<I> type, Injector<I> target);

    /**
     * Adds capability requirement to capability which we are building
     *
     * @param capabilityName name of capability requirement
     * @param type           the class of the value of the dependency
     * @param <I>            the type of the value of the dependency
     * @return this builder
     */
    <I> CapabilityServiceBuilder<T> addCapabilityRequirement(String capabilityName, Class<I> type);

    /**
     * {@inheritDoc}
     * @return this builder
     */
    <I> CapabilityServiceBuilder<T> addDependency(ServiceName dependency, Class<I> type, Injector<I> target);

    /**
     * {@inheritDoc}
     * @return this builder
     */
    @Override
    CapabilityServiceBuilder<T> setInitialMode(ServiceController.Mode mode);
}
