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

import java.util.Collections;
import java.util.Map;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.msc.service.ServiceName;

/**
 * Interface a {@link RuntimeCapability} uses to provide service names
 * to users of the capability.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public interface ServiceNameProvider {

    /**
     * Gets the name of a service provided by the capability.
     *
     * @param serviceType the expected type of the service's value. Cannot be {@code null}
     * @return the name of the service. Will not be {@code null}
     *
     * @throws IllegalArgumentException if {@code serviceType} is {@code null } or
     *            the capability does not provide a service of type {@code serviceType}
     *
     */
    ServiceName getCapabilityServiceName(Class<?> serviceType);

    /**
     * Default {@link ServiceNameProvider} implementation that provides
     * a service name that is created by splitting the capability name
     * on any '.' character.
     */
    class DefaultProvider implements ServiceNameProvider {

        /**
         * Converts a capability name to a service name by splitting the capability name on any '.' character.
         *
         * @param capabilityName the capability name. Cannot be {@code null} or empty
         * @return the service name. Will not be {@code null}
         */
        public static ServiceName capabilityToServiceName(String capabilityName) {
            assert capabilityName != null;
            assert capabilityName.length() > 0;
            return ServiceName.of(capabilityName.split("\\."));
        }

        private final String capabilityName;
        private final Map<Class, ServiceName> serviceNames;

        /**
         * Creates a provider for the given capability that provides no service names.
         * @param capabilityName the capability name. Cannot be {@code null} or empty
         */
        public DefaultProvider(String capabilityName) {
            assert capabilityName != null;
            this.capabilityName = capabilityName;
            this.serviceNames = Collections.emptyMap();
        }

        /**
         * Creates a provider for the given capability that provides no a service name for the given type.
         * @param capabilityName the capability name. Cannot be {@code null} or empty
         * @param legalType the type for which service names should be provided. Cannot be {@code null}
         */
        public DefaultProvider(String capabilityName, Class legalType) {
            assert legalType != null;
            this.capabilityName = capabilityName;
            this.serviceNames = Collections.singletonMap(legalType, capabilityToServiceName(capabilityName));
        }

        /**
         * Creates a provider for the given capability that provides the given service names for the given types.
         * @param capabilityName the capability name. Cannot be {@code null} or empty
         * @param services the mapping of valid types to services names. Cannot be {@code null}, but can be empty
         */
        public DefaultProvider(String capabilityName, Map<Class, ServiceName> services) {
            assert capabilityName != null;
            assert services != null;
            this.capabilityName = capabilityName;
            this.serviceNames = services;
        }

        @Override
        public ServiceName getCapabilityServiceName(Class<?> serviceType) {
            assert serviceType != null;
            ServiceName result = serviceNames.get(serviceType);
            if (result == null) {
                for (Map.Entry<Class, ServiceName> entry : serviceNames.entrySet()) {
                    if (serviceType.isAssignableFrom(entry.getKey())) {
                        return entry.getValue();
                    }
                }
                // No match
                throw ControllerLogger.MGMT_OP_LOGGER.invalidCapabilityServiceType(capabilityName, serviceType);
            }

            return result;
        }
    }
}
