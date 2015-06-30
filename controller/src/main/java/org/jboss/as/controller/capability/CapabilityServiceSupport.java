/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.capability;

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
     * Gets the name of a service associated with a given capability, if there is one.
     * @param capabilityName the name of the capability. Cannot be {@code null}
     * @param serviceType class of the java type that exposes by the service. Cannot be {@code null}
     * @return the name of the service. Will not return {@code null}
     *
     * @throws NoSuchCapabilityException if no matching capability is registered
     * @throws IllegalArgumentException if {@code serviceType} is {@code null } or
     *            the capability does not provide a service of type {@code serviceType}
     */
    ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType) throws NoSuchCapabilityException;

    /**
     * Gets the name of a service associated with a given {@link RuntimeCapability#isDynamicallyNamed() dynamically named}
     * capability, if there is one.
     *
     * @param capabilityBaseName the base name of the capability. Cannot be {@code null}
     * @param dynamicPart the dynamic part of the capability name. Cannot be {@code null}
     * @param serviceType class of the java type that exposes by the service. Cannot be {@code null}
     * @return the name of the service. Will not return {@code null}
     *
     * @throws NoSuchCapabilityException if no matching capability is registered
     * @throws IllegalArgumentException if {@code serviceType} is {@code null } or
     *            the capability does not provide a service of type {@code serviceType}
     */
    ServiceName getCapabilityServiceName(String capabilityBaseName, String dynamicPart, Class<?> serviceType) throws NoSuchCapabilityException;
}
