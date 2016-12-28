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
import java.util.function.Function;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
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
    public static String buildDynamicCapabilityName(String baseName, String ... dynamicNameElement) {
        assert baseName != null;
        assert dynamicNameElement != null;
        assert dynamicNameElement.length > 0;
        StringBuilder sb = new StringBuilder(baseName);
        for (String part:dynamicNameElement){
            sb.append(".").append(part);
        }
        return sb.toString();
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
     *
     * @deprecated use {@link #fromBaseCapability(String...)} on {@code base}
     */
    @Deprecated
    public static <T> RuntimeCapability<T> fromBaseCapability(RuntimeCapability<T> base, String dynamicElement) {
        return base.fromBaseCapability(dynamicElement);
    }

    private final Class<?> serviceValueType;
    private final ServiceName serviceName;
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
        super(name, false, requirements, optionalRequirements, null, null, null, null);
        this.runtimeAPI = runtimeAPI;
        this.serviceValueType = null;
        this.serviceName = null;
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
        super(name, false, new HashSet<>(Arrays.asList(requirements)), null, null, null, null, null);
        this.runtimeAPI = runtimeAPI;
        this.serviceValueType = null;
        this.serviceName = null;
    }

    /**
     * Constructor for use by the builder.
     */
    private RuntimeCapability(Builder<T> builder) {
        super(builder.baseName, builder.dynamic, builder.requirements, builder.optionalRequirements,
                builder.runtimeOnlyRequirements, builder.dynamicRequirements, builder.dynamicOptionalRequirements, builder.dynamicNameMapper);
        this.runtimeAPI = builder.runtimeAPI;
        this.serviceValueType = builder.serviceValueType;
        this.serviceName = ServiceName.parse(builder.baseName);
    }

    /**
     * Constructor for use by {@link #fromBaseCapability(String...)}
     */
    private RuntimeCapability(String baseName, Class<?> serviceValueType, T runtimeAPI,
                              Set<String> requirements, Set<String> optionalRequirements,
                              Set<String> runtimeOnlyRequirements, Set<String> dynamicRequirements,
                              Set<String> dynamicOptionalRequirements, Function<PathAddress,String[]> dynamicNameMapper, String... dynamicElement) {
        super(buildDynamicCapabilityName(baseName, dynamicElement), false, requirements,
                optionalRequirements, runtimeOnlyRequirements, dynamicRequirements, dynamicOptionalRequirements, dynamicNameMapper);
        this.runtimeAPI = runtimeAPI;
        this.serviceValueType = serviceValueType;
        this.serviceName = dynamicElement == null ? ServiceName.parse(baseName) : ServiceName.parse(baseName).append(dynamicElement);
    }

    /**
     * Gets the name of the service provided by this capability, if there is one.
     *
     * @return the name of the service. Will not be {@code null}
     *
     * @throws IllegalArgumentException if the capability does not provide a service
     */
    public ServiceName getCapabilityServiceName() {
        return getCapabilityServiceName((Class<?>) null);
    }

    /**
     * Gets the name of service provided by this capability.
     *
     * @param serviceValueType the expected type of the service's value. Only used to provide validate that
     *                         the service value type provided by the capability matches the caller's
     *                         expectation. May be {@code null} in which case no validation is performed
     *
     * @return the name of the service. Will not be {@code null}
     *
     * @throws IllegalArgumentException if the capability does not provide a service or if its value type
     *                                  is not assignable to {@code serviceValueType}
     */
    public ServiceName getCapabilityServiceName(Class<?> serviceValueType) {
        if (this.serviceValueType == null ||
                (serviceValueType != null && !serviceValueType.isAssignableFrom(this.serviceValueType))) {
            throw ControllerLogger.MGMT_OP_LOGGER.invalidCapabilityServiceType(getName(), serviceValueType);
        }
        return serviceName;
    }

    /**
     * Gets the name of the service provided by this capability, if there is one. Only usable with
     * {@link #isDynamicallyNamed() dynamically named} capabilities.
     *
     * @param dynamicNameElement the dynamic portion of the capability name. Cannot be {@code null}
     *
     * @return the name of the service. Will not be {@code null}
     *
     * @throws IllegalArgumentException if the capability does not provide a service
     * @throws AssertionError if {@link #isDynamicallyNamed()} does not return {@code true}
     */
    public ServiceName getCapabilityServiceName(String dynamicNameElement) {
        return getCapabilityServiceName(dynamicNameElement, null);
    }

    /**
     * Gets the name of service provided by this capability.
     *
     * @param dynamicNameElement the dynamic portion of the capability name. Cannot be {@code null}
     * @param serviceValueType the expected type of the service's value. Only used to provide validate that
     *                         the service value type provided by the capability matches the caller's
     *                         expectation. May be {@code null} in which case no validation is performed
     *
     * @return the name of the service. Will not be {@code null}
     *
     * @throws IllegalArgumentException if the capability does not provide a service or if its value type
     *                                  is not assignable to {@code serviceValueType}
     * @throws IllegalStateException if {@link #isDynamicallyNamed()} does not return {@code true}
     */
    public ServiceName getCapabilityServiceName(String dynamicNameElement, Class<?> serviceValueType) {
        return fromBaseCapability(dynamicNameElement).getCapabilityServiceName(serviceValueType);
    }



    /**
     * Gets the valid type to pass to {@link #getCapabilityServiceName(Class)}.
     *
     * @return  the valid type. May be {@code null} if this capability does not provide a
     *          service
     */
    public Class<?> getCapabilityServiceValueType() {
        return serviceValueType;
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
     * Creates a fully named capability from a {@link #isDynamicallyNamed() dynamically named} base
     * capability. Capability providers should use this method to generate fully named capabilities in logic
     * that handles dynamically named resources.
     *
     * @param dynamicElement the dynamic portion of the full capability name. Cannot be {@code null} or empty
     * @return the fully named capability.
     *
     * @throws AssertionError if {@link #isDynamicallyNamed()} returns {@code false}
     */
    public RuntimeCapability<T> fromBaseCapability(String ... dynamicElement) {
        assert isDynamicallyNamed();
        assert dynamicElement != null;
        assert dynamicElement.length > 0;
        return new RuntimeCapability<T>(getName(), serviceValueType, runtimeAPI,
                getRequirements(), getOptionalRequirements(),
                getRuntimeOnlyRequirements(), getDynamicRequirements(), getDynamicOptionalRequirements(), dynamicNameMapper, dynamicElement);

    }

    /**
     * Creates a fully named capability from a {@link #isDynamicallyNamed() dynamically named} base
     * capability. Capability providers should use this method to generate fully named capabilities in logic
     * that handles dynamically named resources.
     *
     * @param path the dynamic portion of the full capability name. Cannot be {@code null} or empty
     * @return the fully named capability.
     *
     * @throws AssertionError if {@link #isDynamicallyNamed()} returns {@code false}
     */
    public RuntimeCapability<T> fromBaseCapability(PathAddress path) {
        assert isDynamicallyNamed();
        assert path != null;
        String[] dynamicElement = dynamicNameMapper.apply(path);
        assert dynamicElement.length > 0;
        return new RuntimeCapability<>(getName(), serviceValueType, runtimeAPI,
                getRequirements(), getOptionalRequirements(),
                getRuntimeOnlyRequirements(), getDynamicRequirements(), getDynamicOptionalRequirements(), dynamicNameMapper, dynamicElement);

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
        private Class<?> serviceValueType;
        private Set<String> requirements;
        private Set<String> optionalRequirements;
        private Set<String> runtimeOnlyRequirements;
        private Set<String> dynamicRequirements;
        private Set<String> dynamicOptionalRequirements;
        private Function<PathAddress, String[]> dynamicNameMapper = AbstractCapability::addressValueToDynamicName;

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
         * Create a builder for a non-dynamic capability that installs a service with the given value type.
         *
         * @param name  the name of the capability. Cannot be {@code null} or empty.
         * @param serviceValueType the value type of the service installed by the capability
         * @return the builder
         */
        public static Builder<Void> of(String name, Class<?> serviceValueType) {
            return new Builder<Void>(name, false, null).setServiceType(serviceValueType);
        }

        /**
         * Create a builder for a possibly dynamic capability that installs a service with the given value type.
         *
         * @param name  the name of the capability. Cannot be {@code null} or empty.
         * @param dynamic {@code true} if the capability is a base capability for dynamically named capabilities
         * @param serviceValueType the value type of the service installed by the capability
         * @return the builder
         */
        public static Builder<Void> of(String name, boolean dynamic, Class<?> serviceValueType) {
            return new Builder<Void>(name, dynamic, null).setServiceType(serviceValueType);
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
         * Sets that the capability installs a service with the given value type.
         * @param type the value type of the service installed by the capability. May be {@code null}
         * @return the builder
         */
        public Builder<T> setServiceType(Class<?> type) {
            this.serviceValueType = type;
            return this;
        }

        /**
         * Adds the names of other capabilities that this capability requires. The requirement
         * for these capabilities will automatically be registered when this capability is registered.
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
         *
         * @deprecated nothing is currently done with this data and unless a true use is implemented this method may be removed
         */
        @Deprecated
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
         *
         * @deprecated nothing is currently done with this data and unless a true use is implemented this method may be removed
         */
        @Deprecated
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
         *
         * @deprecated nothing is currently done with this data and unless a true use is implemented this method may be removed
         */
        @Deprecated
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
         *
         * @deprecated nothing is currently done with this data and unless a true use is implemented this method may be removed
         */
        @Deprecated
        public Builder<T> addDynamicOptionalRequirements(String... requirements) {
            assert requirements != null;
            if (this.dynamicOptionalRequirements == null) {
                this.dynamicOptionalRequirements = new HashSet<>(requirements.length);
            }
            Collections.addAll(this.dynamicOptionalRequirements, requirements);
            return this;
        }

        /**
         * Sets dynamic name mapper, can be used for cases when you need to customize dynamic name
         *
         * @param mapper function
         * @return the builder
         */
        public Builder<T> setDynamicNameMapper(Function<PathAddress,String[]> mapper) {
            assert mapper != null;
            this.dynamicNameMapper = mapper;
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
    }
}
