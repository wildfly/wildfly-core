/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
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

package org.jboss.as.controller.capability.registry;

import java.util.Set;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.msc.service.ServiceName;

/**
 * A read-only view of {@link CapabilityRegistry}
 *
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface ImmutableCapabilityRegistry {
    /**
     * Gets whether a capability with the given name is registered.
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @param dependentName
     * @param context the context in which to check for the capability
     * @return {@code true} if there is a capability with the given name registered
     *
     */
    boolean hasCapability(String capabilityName, String dependentName, CapabilityContext context);

    /**
     * Gets the runtime API associated with a given capability, if there is one.
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @param context the context in which to resolve the capability. Cannot be {@code null}
     * @param apiType class of the java type that exposes the API. Cannot be {@code null}
     * @param <T> the java type that exposes the API
     * @return the runtime API. Will not return {@code null}
     *
     * @throws IllegalArgumentException if the capability does not provide a runtime API
     * @throws ClassCastException if the runtime API exposed by the capability cannot be cast to type {code T}
     */
    <T> T getCapabilityRuntimeAPI(String capabilityName, CapabilityContext context, Class<T> apiType);

    /**
     * Returns possible registration points for capabilityId
     * @param capabilityId id of capability
     * @return run set of {@link RegistrationPoint} or empty set if no registration points are found
     */
    Set<RegistrationPoint> getPossibleProviderPoints(CapabilityId capabilityId);

    Set<CapabilityRegistration> getCapabilities();

    Set<CapabilityRegistration> getPossibleCapabilities();

    ServiceName getCapabilityServiceName(String capabilityName, CapabilityContext context, Class<?> serviceType);
}
