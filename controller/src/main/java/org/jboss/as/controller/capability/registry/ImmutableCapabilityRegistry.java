/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import java.util.Set;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.PathAddress;
import org.jboss.msc.service.ServiceName;

/**
 * A read-only view of {@link CapabilityRegistry}
 *
 * Capability registry contains two kinds of capabilities:
 *  - possible capabilities which are defined on each resource to provide what said resource and provide at runtime
 *  - runtime or actual capabilities which are runtime instances of possible capabilities
 *
 *
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface ImmutableCapabilityRegistry {
    /**
     * Gets whether a runtime capability with the given name is registered.
     *
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @param scope        the scope in which to check for the capability
     * @return {@code true} if there is a capability with the given name registered
     */
    boolean hasCapability(String capabilityName, CapabilityScope scope);
    /**
     * Gets the runtime API associated with a given capability, if there is one.
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @param scope the scope in which to resolve the capability. Cannot be {@code null}
     * @param apiType class of the java type that exposes the API. Cannot be {@code null}
     * @param <T> the java type that exposes the API
     * @return the runtime API. Will not return {@code null}
     *
     * @throws IllegalArgumentException if the capability does not provide a runtime API
     * @throws ClassCastException if the runtime API exposed by the capability cannot be cast to type {code T}
     */
    <T> T getCapabilityRuntimeAPI(String capabilityName, CapabilityScope scope, Class<T> apiType);

    /**
     * Returns set of runtime capabilities registered in the registry
     *
     * @return read only {@link Set} with all runtime capabilities and where ware they registered
     */
    Set<CapabilityRegistration<?>> getCapabilities();

    /**
     * Returns set of possible capabilities with there registration points registered in the registry
     *
     * @return read only {@link Set} with all possible capabilities and where ware they registered
     */
    Set<CapabilityRegistration<?>> getPossibleCapabilities();

    /**
     * Gets the name of the service provided by the capability, if there is one.
     *
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @param scope the scope in which to resolve the capability. Cannot be {@code null}
     * @param serviceType the type of the value provided by the service. May be {@code null} if the caller is
     *                    unconcerned about checking that its understanding of the service type provided by the
     *                    capability is correct
     * @return the service name. Will not return {@code null}
     *
     * @throws java.lang.IllegalStateException if no capability with the given name is available in the given context
     * @throws java.lang.IllegalArgumentException if the capability does not provide a service, or if {@code serviceType}
     *             is not {@code null} and the type of the service the capability provides is not assignable from it
     */
    ServiceName getCapabilityServiceName(String capabilityName, CapabilityScope scope, Class<?> serviceType);

    /**
     * Returns possible provider points for passed capabilityId
     * @param capabilityId id of capability
     * @return set of PathAddress-es where capability could be registered, will not return <code>null</code> but can be empty
     */

    Set<PathAddress> getPossibleProviderPoints(CapabilityId capabilityId);

    /**
     * returns capability registration for capability id pass as parameter
     * @param capabilityId id of capability with its scope.
     * @return CapabilityRegistration or null if none is found
     */
    CapabilityRegistration<?> getCapability(CapabilityId capabilityId);

    /**
     * Retrieve all the capability names that the passed scope can access
     * @param referencedCapability The static name of the capability
     * @param dependentScope The scope from which the capability is referenced
     * @return A set of capabilities name. Only the dynamic part of the name is returned
     */
    Set<String> getDynamicCapabilityNames(String referencedCapability,
            CapabilityScope dependentScope);
}
