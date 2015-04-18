/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
import java.util.HashSet;
import java.util.Set;

/**
 * A capability exposed in a running WildFly process.
 *
 * @param <T> the type of the runtime API object exposed by the capability
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RuntimeCapability<T> extends AbstractCapability  {

    private final ServiceNameProvider serviceNameProvider;
    private final T runtimeAPI;

    /**
     * Creates a new capability
     * @param name the name of the capability. Cannot be {@code null}
     * @param runtimeAPI implementation of the API exposed by this capability to other capabilities. May be {@code null}
     * @param requirements names of other capabilities upon which this capability has a hard requirement. May be {@code null}
     * @param optionalRequirements names of other capabilities upon which this capability has an optional requirement. May be {@code null}
     *
     * @deprecated use a {@link org.jboss.as.controller.capability.RuntimeCapability.Builder}
     */
    @Deprecated
    public RuntimeCapability(String name, T runtimeAPI, Set<String> requirements, Set<String> optionalRequirements) {
        super(name, requirements, optionalRequirements);
        this.runtimeAPI = runtimeAPI;
        this.serviceNameProvider = new ServiceNameProvider.DefaultProvider(name);
    }

    /**
     * Creates a new capability
     * @param name the name of the capability. Cannot be {@code null}
     * @param runtimeAPI implementation of the API exposed by this capability to other capabilities. May be {@code null}
     * @param requirements names of other capabilities upon which this capability has a hard requirement. May be {@code null}
     *
     * @deprecated use a {@link org.jboss.as.controller.capability.RuntimeCapability.Builder}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public RuntimeCapability(String name, T runtimeAPI, Set<String> requirements) {
        this(name, runtimeAPI, requirements, null);
    }

    /**
     * Creates a new capability
     * @param name the name of the capability. Cannot be {@code null}
     * @param runtimeAPI implementation of the API exposed by this capability to other capabilities. May be {@code null}
     * @param requirements names of other capabilities upon which this capability has a hard requirement. May be {@code null}
     *
     * @deprecated use a {@link org.jboss.as.controller.capability.RuntimeCapability.Builder}
     */
    @Deprecated
    public RuntimeCapability(String name, T runtimeAPI, String... requirements) {
        super(name, requirements);
        this.runtimeAPI = runtimeAPI;
        this.serviceNameProvider = new ServiceNameProvider.DefaultProvider(name);
    }

    /**
     * Creates a new capability
     *
     * @param builder builder for the capability. Cannot be {@code null}
     */
    private RuntimeCapability(Builder<T> builder) {
        super(builder.name, builder.requirements, builder.optionalRequirements);
        this.runtimeAPI = builder.runtimeAPI;
        this.serviceNameProvider = builder.getServiceNameProvider();
    }

    /**
     * Gets the provider of service names that callers can use to determine
     * the name of service provided by this capability.
     *
     * @return the service name provider. Will not be {@code null}
     */
    public ServiceNameProvider getServiceNameProvider() {
        return serviceNameProvider;
    }

    /**
     * Object encapsulating the API exposed by this capability to other capabilities that require it, if it does
     * expose such an API.
     *
     * @return the API object, or {@code null} if the capability exposes no API to other capabilities
     */
    public T getRuntimeAPI() {
        return runtimeAPI;
    }

    /**
     * Builder for a {@link RuntimeCapability}.
     *
     * @param <T> the type of the runtime API object exposed by the capability
     */
    public static class Builder<T> {
        private final String name;
        private final T runtimeAPI;
        private ServiceNameProvider serviceNameProvider;
        private Set<String> requirements;
        private Set<String> optionalRequirements;

        /**
         * Create a builder for a capability with no custom runtime API.
         * @param name the name of the capability. Cannot be {@code null} or empty.
         * @return the builder
         */
        public static Builder<Void> of(String name) {
            return new Builder<Void>(name, null);
        }

        /**
         * Create a builder for a capability that installs services with the given value type.
         * A {@link org.jboss.as.controller.capability.ServiceNameProvider.DefaultProvider default service name provider}
         * will be used by the capability.
         * @param name  the name of the capability. Cannot be {@code null} or empty.
         * @param serviceType the value type of the service installed by the capability
         * @return the builder
         */
        public static Builder<Void> of(String name, Class<?> serviceType) {
            return new Builder<Void>(name, null).setServiceType(serviceType);
        }

        /**
         * Create a builder for a capability that provides the given custom runtime API.
         * @param name the name of the capability. Cannot be {@code null} or empty.
         * @param runtimeAPI the custom API implementation exposed by the capability
         * @param <T> the type of the runtime API object exposed by the capability
         * @return the builder
         */
        public static <T> Builder<T> of(String name, T runtimeAPI) {
            return new Builder<T>(name, runtimeAPI);
        }

        private Builder(String name, T runtimeAPI) {
            assert name != null;
            assert name.length() > 0;
            this.name = name;
            this.runtimeAPI = runtimeAPI;
        }

        /**
         * Sets that the capability installs services with the given value type and that a
         * {@link org.jboss.as.controller.capability.ServiceNameProvider.DefaultProvider default service name provider}
         * should be used by the capability.
         * @param type the value type of the service installed by the capability. Cannot be {@code null}
         * @return the builder
         *
         * @throws IllegalStateException if a {@code ServiceNameProvider} or service type has previously been configured
         */
        public Builder<T> setServiceType(Class<?> type) {
            return setServiceNameProvider(new ServiceNameProvider.DefaultProvider(name, type));
        }

        /**
         * Sets the provider of service type to service name mappings that the capability should use.
         *
         * @param provider a provider of service type to service name mappings
         * @return the builder
         *
         * @throws IllegalStateException if a {@code ServiceNameProvider} or service type has previously been configured
         */
        public Builder<T> setServiceNameProvider(ServiceNameProvider provider) {
            if (serviceNameProvider != null) {
                throw new IllegalStateException();
            }
            this.serviceNameProvider = provider;
            return this;
        }

        /**
         * Adds the names of other capabilities that this capability requires.
         *
         * @param requirements the capability names
         * @return the builder
         */
        public Builder<T> addRequirements(String... requirements) {
            assert requirements != null;
            if (this.requirements == null) {
                this.requirements = new HashSet<>(requirements.length);
            }
            Collections.addAll(this.requirements, requirements);
            return this;
        }

        /**
         * Adds the names of other capabilities that this capability optionally requires.
         * @param requirements the capability names
         * @return the builder
         */
        public Builder<T> addOptionalRequirements(String... requirements) {
            assert requirements != null;
            if (this.optionalRequirements == null) {
                this.optionalRequirements = new HashSet<>(requirements.length);
            }
            Collections.addAll(this.optionalRequirements, requirements);
            return this;
        }

        /**
         * Builds the capability.
         *
         * @return the capability. Will not return {@code null}
         */
        public RuntimeCapability<T> build() {
            return new RuntimeCapability<>(this);
        }

        private ServiceNameProvider getServiceNameProvider() {
            return serviceNameProvider == null ? new ServiceNameProvider.DefaultProvider(name) : serviceNameProvider;
        }
    }
}
