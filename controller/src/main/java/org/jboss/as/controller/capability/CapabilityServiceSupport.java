/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability;

import java.util.Optional;

import org.jboss.msc.service.ServiceName;

/**
 * Provides support for capability integration outside the management layer,
 * in service implementations.
 * <p>
 * Note that use of this interface in no way creates a requirement on the
 * referenced capability by the caller.
 *
 * @author Brian Stansberry
 */
public interface CapabilityServiceSupport {

    /**
     * Exception thrown when support for an unregistered capability is requested. This is a checked
     * exception because {@code CapabilityServiceSupport} is used outside the management layer and
     * the requirements for capability availability available in {@link org.jboss.as.controller.OperationContext}
     * are not possible. So callers need to be aware of and handle non-existent capabilities.
     */
    class NoSuchCapabilityException extends Exception {

        static final long serialVersionUID = 1L;

        public NoSuchCapabilityException(String message) {
            super(message);
        }
    }
    /**
     * Gets whether a runtime capability with the given name is registered.
     *
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @return {@code true} if there is a capability with the given name registered
     */
    boolean hasCapability(String capabilityName);

    /**
     * Gets the runtime API associated with a given capability, if there is one.
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @param apiType class of the java type that exposes the API. Cannot be {@code null}
     * @param <T> the java type that exposes the API
     * @return the runtime API. Will not return {@code null}
     *
     * @throws NoSuchCapabilityException if no matching capability is registered
     * @throws java.lang.IllegalArgumentException if the capability does not provide a runtime API
     * @throws java.lang.ClassCastException if the runtime API exposed by the capability cannot be cast to type {code T}
     */
    <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) throws NoSuchCapabilityException;

    /**
     * Gets the runtime API associated with a given {@link RuntimeCapability#isDynamicallyNamed() dynamically named}
     * capability, if there is one.
     *
     * @param capabilityBaseName the base name of the capability. Cannot be {@code null}
     * @param dynamicPart the dynamic part of the capability name. Cannot be {@code null}
     * @param apiType class of the java type that exposes the API. Cannot be {@code null}
     * @param <T> the java type that exposes the API
     * @return the runtime API. Will not return {@code null}
     *
     * @throws NoSuchCapabilityException if no matching capability is registered
     * @throws java.lang.IllegalArgumentException if the capability does not provide a runtime API
     * @throws java.lang.ClassCastException if the runtime API exposed by the capability cannot be cast to type {code T}
     */
    <T> T getCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) throws NoSuchCapabilityException;

    /**
     * Gets the runtime API associated with a given capability, if there is one.
     *
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @param apiType        class of the java type that exposes the API. Cannot be {@code null}
     * @param <T>            the java type that exposes the API
     *
     * @return an Optional describing the value of the runtime API. If no matching capability is registered, the optional
     * will be empty.
     *
     * @throws java.lang.IllegalArgumentException if the capability does not provide a runtime API
     * @throws java.lang.ClassCastException       if the runtime API exposed by the capability cannot be cast to type {code T}
     */
    <T> Optional<T> getOptionalCapabilityRuntimeAPI(String capabilityName, Class<T> apiType);

    /**
     * Gets the runtime API associated with a given {@link RuntimeCapability#isDynamicallyNamed() dynamically named}
     * capability, if there is one.
     *
     * @param capabilityBaseName the base name of the capability. Cannot be {@code null}
     * @param dynamicPart        the dynamic part of the capability name. Cannot be {@code null}
     * @param apiType            class of the java type that exposes the API. Cannot be {@code null}
     * @param <T>                the java type that exposes the API
     *
     * @return an Optional describing the value of the runtime API. If no matching capability is registered, the optional
     * will be empty.
     *
     * @throws java.lang.IllegalArgumentException if the capability does not provide a runtime API
     * @throws java.lang.ClassCastException       if the runtime API exposed by the capability cannot be cast to type {code T}
     */
    <T> Optional<T> getOptionalCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType);

    /**
     * Gets the name of a service associated with a given capability. This method does not confirm that the
     * capability is currently registered.
     *
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @return the name of the service. Will not return {@code null}
     */
    ServiceName getCapabilityServiceName(String capabilityName);

    /**
     * Gets the name of a service associated with a given {@link RuntimeCapability#isDynamicallyNamed() dynamically named}
     * capability. This method does not confirm that the capability is currently registered.
     *
     * @param capabilityBaseName the base name of the capability. Cannot be {@code null}
     * @param dynamicParts the dynamic parts of the capability name. Cannot be {@code null} Can be multiple if capability supports that
     * @return the name of the service. Will not return {@code null}
     */
    ServiceName getCapabilityServiceName(String capabilityBaseName, String ... dynamicParts);
}
