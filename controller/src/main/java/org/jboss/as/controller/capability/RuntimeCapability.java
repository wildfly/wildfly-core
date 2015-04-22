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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.msc.service.ServiceName;

/**
 * A capability exposed in a running WildFly process.
 *
 * @param <T> the type of the runtime API object exposed by the capability
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RuntimeCapability<T> extends AbstractCapability  {

    /**
     * Constructs a full capability name from a static base name and a dynamic element.
     *
     * @param baseName the base name. Cannot be {@code null}
     * @param dynamicNameElement  the dynamic portion of the name. Cannot be {@code null}
     * @return the full capability name. Will not return {@code null}
     */
    public static String buildDynamicCapabilityName(String baseName, String dynamicNameElement) {
        assert baseName != null;
        assert dynamicNameElement != null;
        return baseName + "." + dynamicNameElement;
    }

    /**
     * Creates a fully named capability from a {@link #isDynamicallyNamed() dynamically named} base
     * capability. Capability providers should use this method to generate fully named capabilities in logic
     * that handles dynamically named resources.
     *
     * @param base the base capability. Cannot be {@code null}, and {@link #isDynamicallyNamed()} must return {@code true}
     * @param dynamicElement the dynamic portion of the full capability name. Cannot be {@code null} or empty
     * @param <T> the type of the runtime API object exposed by the capability
     * @return the fully name capability.
     */
    public static <T> RuntimeCapability<T> fromBaseCapability(RuntimeCapability<T> base, String dynamicElement) {
        assert base != null;
        assert base.isDynamicallyNamed();
        assert dynamicElement != null;
        assert dynamicElement.length() > 0;
        return new RuntimeCapability<T>(base.getName(), dynamicElement, base.serviceNameProvider, base.runtimeAPI,
                base.getRequirements(), base.getOptionalRequirements(),
                base.getRuntimeOnlyRequirements(), base.getDynamicRequirements(), base.getDynamicOptionalRequirements());
    }

    private final ServiceNameProvider serviceNameProvider;
    private final T runtimeAPI;
    private final String dynamicNameElement;

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
        super(name, false, requirements, optionalRequirements, null, null, null);
        this.runtimeAPI = runtimeAPI;
        this.serviceNameProvider = new ServiceNameProvider.DefaultProvider(name);
        this.dynamicNameElement = null;
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
        super(name, false, new HashSet<>(Arrays.asList(requirements)), null, null, null, null);
        this.runtimeAPI = runtimeAPI;
        this.serviceNameProvider = new ServiceNameProvider.DefaultProvider(name);
        this.dynamicNameElement = null;
    }

    /**
     * Constructor for use by the builder.
     */
    private RuntimeCapability(Builder<T> builder) {
        super(builder.baseName, builder.dynamic, builder.requirements, builder.optionalRequirements,
                builder.runtimeOnlyRequirements, builder.dynamicRequirements, builder.dynamicOptionalRequirements);
        this.runtimeAPI = builder.runtimeAPI;
        this.serviceNameProvider = builder.getServiceNameProvider();
        this.dynamicNameElement = null;
    }

    /**
     * Constructor for use by {@link #fromBaseCapability(RuntimeCapability, String)}
     */
    private RuntimeCapability(String baseName, String dynamicElement, ServiceNameProvider provider, T runtimeAPI,
                              Set<String> requirements, Set<String> optionalRequirements,
                              Set<String> runtimeOnlyRequirements, Set<String> dynamicRequirements,
                              Set<String> dynamicOptionalRequirements) {
        super(buildDynamicCapabilityName(baseName, dynamicElement), false, requirements,
                optionalRequirements, runtimeOnlyRequirements, dynamicRequirements, dynamicOptionalRequirements);
        this.runtimeAPI = runtimeAPI;
        this.serviceNameProvider = provider;
        this.dynamicNameElement = dynamicElement;
    }

    /**
     * Gets the name of service provided by this capability whose value is of the given type.
     *
     * @param serviceType the expected type of the service's value. Cannot be {@code null}
     * @return the name of the service. Will not be {@code null}
     *
     * @throws IllegalArgumentException if {@code serviceType} is {@code null } or
     *            the capability does not provide a service of type {@code serviceType}
     */
    public ServiceName getCapabilityServiceName(Class serviceType) {
        return dynamicNameElement == null
                ? serviceNameProvider.getCapabilityServiceName(serviceType)
                : serviceNameProvider.getCapabilityServiceName(serviceType, dynamicNameElement);
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
        private final String baseName;
        private final T runtimeAPI;
        private final boolean dynamic;
        private ServiceNameProvider serviceNameProvider;
        private Set<String> requirements;
        private Set<String> optionalRequirements;
        private Set<String> runtimeOnlyRequirements;
        private Set<String> dynamicRequirements;
        private Set<String> dynamicOptionalRequirements;

        /**
         * Create a builder for a non-dynamic capability with no custom runtime API.
         * @param name the name of the capability. Cannot be {@code null} or empty.
         * @return the builder
         */
        public static Builder<Void> of(String name) {
            return new Builder<Void>(name, false, null);
        }

        /**
         * Create a builder for a possibly dynamic capability with no custom runtime API.
         * @param name the name of the capability. Cannot be {@code null} or empty.
         * @param dynamic {@code true} if the capability is a base capability for dynamically named capabilities
         * @return the builder
         */
        public static Builder<Void> of(String name, boolean dynamic) {
            return new Builder<Void>(name, dynamic, null);
        }

        /**
         * Create a builder for a non-dynamic capability that installs services with the given value type.
         * A {@link org.jboss.as.controller.capability.ServiceNameProvider.DefaultProvider default service name provider}
         * will be used by the capability.
         * @param name  the name of the capability. Cannot be {@code null} or empty.
         * @param serviceType the value type of the service installed by the capability
         * @return the builder
         */
        public static Builder<Void> of(String name, Class<?> serviceType) {
            return new Builder<Void>(name, false, null).setServiceType(serviceType);
        }

        /**
         * Create a builder for a possibly dynamic capability that installs services with the given value type.
         * A {@link org.jboss.as.controller.capability.ServiceNameProvider.DefaultProvider default service name provider}
         * will be used by the capability.
         * @param name  the name of the capability. Cannot be {@code null} or empty.
         * @param dynamic {@code true} if the capability is a base capability for dynamically named capabilities
         * @param serviceType the value type of the service installed by the capability
         * @return the builder
         */
        public static Builder<Void> of(String name, boolean dynamic, Class<?> serviceType) {
            return new Builder<Void>(name, dynamic, null).setServiceType(serviceType);
        }

        /**
         * Create a builder for a non-dynamic capability that provides the given custom runtime API.
         * @param name the name of the capability. Cannot be {@code null} or empty.
         * @param runtimeAPI the custom API implementation exposed by the capability
         * @param <T> the type of the runtime API object exposed by the capability
         * @return the builder
         */
        public static <T> Builder<T> of(String name, T runtimeAPI) {
            return new Builder<T>(name, false, runtimeAPI);
        }

        /**
         * Create a builder for a possibly dynamic capability that provides the given custom runtime API.
         * @param name the name of the capability. Cannot be {@code null} or empty.
         * @param dynamic {@code true} if the capability is a base capability for dynamically named capabilities
         * @param runtimeAPI the custom API implementation exposed by the capability
         * @param <T> the type of the runtime API object exposed by the capability
         * @return the builder
         */
        public static <T> Builder<T> of(String name, boolean dynamic, T runtimeAPI) {
            return new Builder<T>(name, dynamic, runtimeAPI);
        }

        private Builder(String baseName, boolean dynamic, T runtimeAPI) {
            assert baseName != null;
            assert baseName.length() > 0;
            this.baseName = baseName;
            this.runtimeAPI = runtimeAPI;
            this.dynamic = dynamic;
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
            return setServiceNameProvider(new ServiceNameProvider.DefaultProvider(baseName, type));
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
         * Adds the names of other capabilities that this capability optionally uses,
         * but only if they are present in the runtime. The persistent configuration of
         * the capability being built will never mandate the presence of these capabilities.
         *
         * @param requirements the capability names
         * @return the builder
         */
        public Builder<T> addRuntimeOnlyRequirements(String... requirements) {
            assert requirements != null;
            if (this.runtimeOnlyRequirements == null) {
                this.runtimeOnlyRequirements = new HashSet<>(requirements.length);
            }
            Collections.addAll(this.runtimeOnlyRequirements, requirements);
            return this;
        }

        /**
         * Adds the the names of other dynamically named capabilities upon a concrete instance of which this
         * capability will have a hard requirement once the full name is known
         *
         * @param requirements the capability names
         * @return the builder
         */
        public Builder<T> addDynamicRequirements(String... requirements) {
            assert requirements != null;
            if (this.dynamicRequirements == null) {
                this.dynamicRequirements = new HashSet<>(requirements.length);
            }
            Collections.addAll(this.dynamicRequirements, requirements);
            return this;
        }

        /**
         * Adds the the names of other dynamically named capabilities upon a concrete instance of which this
         * capability will have an optional requirement once the full name is known
         *
         * @param requirements the capability names
         * @return the builder
         */
        public Builder<T> addDynamicOptionalRequirements(String... requirements) {
            assert requirements != null;
            if (this.dynamicOptionalRequirements == null) {
                this.dynamicOptionalRequirements = new HashSet<>(requirements.length);
            }
            Collections.addAll(this.dynamicOptionalRequirements, requirements);
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
            return serviceNameProvider == null ? new ServiceNameProvider.DefaultProvider(baseName) : serviceNameProvider;
        }
    }
}
