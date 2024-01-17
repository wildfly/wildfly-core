/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.version.Stability;
import org.jboss.msc.service.ServiceName;
import org.wildfly.common.Assert;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A capability exposed in a running WildFly process.
 *
 * @param <T> the type of the runtime API object exposed by the capability
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RuntimeCapability<T> implements Capability {

    //todo remove, here only for binary compatibility of elytron subsystem, drop once it is in.
    public static String buildDynamicCapabilityName(String baseName, String dynamicNameElement) {
        return buildDynamicCapabilityName(baseName, new String[]{dynamicNameElement});
    }

    //only here for binary compatibility, remove once elytron subsystem lands
    public RuntimeCapability<T> fromBaseCapability(String dynamicElement) {
        return fromBaseCapability(new String[]{dynamicElement});
    }
    //end remove

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
     * Resolves the full capability name from a unary service descriptor and reference.
     * @param descriptor a service descriptor
     * @param name the dynamic name component
     * @return the full capability name.
     */
    public static <T> String resolveCapabilityName(UnaryServiceDescriptor<T> descriptor, String name) {
        Map.Entry<String, String[]> resolved = descriptor.resolve(name);
        return buildDynamicCapabilityName(resolved.getKey(), resolved.getValue());
    }

    /**
     * Resolves the full capability name from a binary service descriptor and references.
     * @param descriptor a service descriptor
     * @param parent the first dynamic name component
     * @param child the second dynamic name component
     * @return the full capability name.
     */
    public static <T> String resolveCapabilityName(BinaryServiceDescriptor<T> descriptor, String parent, String child) {
        Map.Entry<String, String[]> resolved = descriptor.resolve(parent, child);
        return buildDynamicCapabilityName(resolved.getKey(), resolved.getValue());
    }

    /**
     * Resolves the full capability name from a ternary service descriptor and references.
     * @param descriptor a service descriptor
     * @param grandparent the first dynamic name component
     * @param parent the second dynamic name component
     * @param child the third dynamic name component
     * @return the full capability name.
     */
    public static <T> String resolveCapabilityName(TernaryServiceDescriptor<T> descriptor, String grandparent, String parent, String child) {
        Map.Entry<String, String[]> resolved = descriptor.resolve(grandparent, parent, child);
        return buildDynamicCapabilityName(resolved.getKey(), resolved.getValue());
    }

    /**
     * Resolves the full capability name from a ternary service descriptor and references.
     * @param descriptor a service descriptor
     * @param greatGrandparent the first dynamic name component
     * @param grandparent the second dynamic name component
     * @param parent the third dynamic name component
     * @param child the fourth dynamic name component
     * @return the full capability name.
     */
    public static <T> String resolveCapabilityName(QuaternaryServiceDescriptor<T> descriptor, String greatGrandparent, String grandparent, String parent, String child) {
        Map.Entry<String, String[]> resolved = descriptor.resolve(greatGrandparent, grandparent, parent, child);
        return buildDynamicCapabilityName(resolved.getKey(), resolved.getValue());
    }

    // Default value for allowMultipleRegistrations.
    private static final boolean ALLOW_MULTIPLE = false;


    private final String name;
    private final boolean dynamic;
    private final Set<String> requirements;
    private final Function<PathAddress,String[]> dynamicNameMapper;
    private final Class<?> serviceValueType;
    private volatile ServiceName serviceName;
    private final T runtimeAPI;
    private final boolean allowMultipleRegistrations;
    private final Stability stability;

    /**
     * Constructor for use by the builder.
     */
    private RuntimeCapability(Builder<T> builder) {
        assert builder.baseName != null;
        this.name = builder.baseName;
        this.dynamic = builder.dynamic;
        this.requirements = establishRequirements(builder.requirements);
        this.dynamicNameMapper = Objects.requireNonNullElse(builder.dynamicNameMapper, UnaryCapabilityNameResolver.DEFAULT);
        this.runtimeAPI = builder.runtimeAPI;
        this.serviceValueType = builder.serviceValueType;
        this.allowMultipleRegistrations = builder.allowMultipleRegistrations;
        this.stability = builder.stability;
    }

    private static Set<String> establishRequirements(Set<String> input) {
        if (input != null && !input.isEmpty()) {
            return Set.copyOf(input);
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Constructor for use by {@link #fromBaseCapability(String...)}
     */
    private RuntimeCapability(String baseName, Class<?> serviceValueType, ServiceName baseServiceName, T runtimeAPI,
                              Set<String> requirements,
                              boolean allowMultipleRegistrations,
                              Function<PathAddress, String[]> dynamicNameMapper,
                              Stability stability,
                              String... dynamicElement
    ) {
        this.name = buildDynamicCapabilityName(baseName, dynamicElement);
        this.dynamic = false;
        this.requirements = establishRequirements(requirements);
        this.dynamicNameMapper = Objects.requireNonNullElse(dynamicNameMapper, UnaryCapabilityNameResolver.DEFAULT);
        this.runtimeAPI = runtimeAPI;
        this.serviceValueType = serviceValueType;
        if (serviceValueType != null) {
            Assert.assertNotNull(baseServiceName);
            this.serviceName = baseServiceName.append(dynamicElement);
        } else {
            assert baseServiceName == null;
        }
        this.allowMultipleRegistrations = allowMultipleRegistrations;
        this.stability = stability;
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
        return getServiceName();
    }

    /**
     * Gets the name of the service provided by this capability, if there is one. Only usable with
     * {@link #isDynamicallyNamed() dynamically named} capabilities.
     *
     * @param dynamicNameElements the dynamic portion of the capability name. Cannot be {@code null}
     *
     * @return the name of the service. Will not be {@code null}
     *
     * @throws IllegalArgumentException if the capability does not provide a service
     * @throws AssertionError if {@link #isDynamicallyNamed()} does not return {@code true}
     */
    public ServiceName getCapabilityServiceName(String... dynamicNameElements) {
        return getCapabilityServiceName(null, dynamicNameElements);
    }

    /**
     * Gets the name of the service provided by this capability, if there is one. Only usable with
     * {@link #isDynamicallyNamed() dynamically named} capabilities.
     *
     * @param address Path address for which service name is calculated from Cannot be {@code null}
     *
     * @return the name of the service. Will not be {@code null}
     *
     * @throws IllegalArgumentException if the capability does not provide a service
     * @throws AssertionError if {@link #isDynamicallyNamed()} does not return {@code true}
     */
    public ServiceName getCapabilityServiceName(PathAddress address) {
        return getCapabilityServiceName(address, null);
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
        return getCapabilityServiceName(serviceValueType, dynamicNameElement);
    }

    public ServiceName getCapabilityServiceName(Class<?> serviceValueType, String... dynamicNameElements) {
        return fromBaseCapability(dynamicNameElements).getCapabilityServiceName(serviceValueType);
    }

    /**
     * Gets the name of service provided by this capability.
     *
     * @param address the path from which dynamic portion of the capability name is calculated from. Cannot be {@code null}
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
    public ServiceName getCapabilityServiceName(PathAddress address, Class<?> serviceValueType) {
        return fromBaseCapability(address).getCapabilityServiceName(serviceValueType);
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
     * Gets whether this capability can be registered at more than one point within the same
     * overall scope.
     *
     * @return {@code true} if the capability can legally be registered in more than one location in the same scope;
     *         {@code false} if an attempt to do this should result in an exception
     */
    public boolean isAllowMultipleRegistrations() {
        return allowMultipleRegistrations;
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
        return new RuntimeCapability<>(getName(), serviceValueType, getServiceName(), runtimeAPI,
                getRequirements(), allowMultipleRegistrations,dynamicNameMapper, this.stability, dynamicElement);

    }

    private ServiceName getServiceName() {
        ServiceName result = serviceName;
        if (result == null && serviceValueType != null) {
            result = this.serviceName = ServiceNameFactory.parseServiceName(getName());
        }
        return  result;
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
        return new RuntimeCapability<>(getName(), serviceValueType, getServiceName(), runtimeAPI,
                getRequirements(), allowMultipleRegistrations, dynamicNameMapper, this.stability, dynamicElement);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getRequirements() {
        return requirements;
    }

    @Override
    public boolean isDynamicallyNamed() {
        return dynamic;
    }

    @Override
    public String getDynamicName(String dynamicNameElement) {
        if (!dynamic) {
            throw new IllegalStateException();
        }
        return name + "." + dynamicNameElement;
    }

    @Override
    public String getDynamicName(PathAddress address) {
        if (!dynamic) {
            throw new IllegalStateException();
        }
        String[] dynamicElements = dynamicNameMapper.apply(address);
        return RuntimeCapability.buildDynamicCapabilityName(name, dynamicElements);
    }

    @Override
    public Stability getStability() {
        return this.stability;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RuntimeCapability<?> that = (RuntimeCapability<?>) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
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
        private boolean allowMultipleRegistrations = ALLOW_MULTIPLE;
        private Function<PathAddress, String[]> dynamicNameMapper = UnaryCapabilityNameResolver.DEFAULT;
        private Stability stability = Stability.DEFAULT;

        /**
         * Create a builder for a non-dynamic capability with no custom runtime API.
         * @param name the name of the capability. Cannot be {@code null} or empty.
         * @return the builder
         */
        public static Builder<Void> of(String name) {
            return new Builder<>(name, false, null);
        }

        /**
         * Create a builder for a possibly dynamic capability with no custom runtime API.
         * @param name the name of the capability. Cannot be {@code null} or empty.
         * @param dynamic {@code true} if the capability is a base capability for dynamically named capabilities
         * @return the builder
         */
        public static Builder<Void> of(String name, boolean dynamic) {
            return new Builder<>(name, dynamic, null);
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
            return new Builder<>(name, false, runtimeAPI);
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
            return new Builder<>(name, dynamic, runtimeAPI);
        }

        /**
         * Creates a builder for a non-dynamic capability using the name and type of the specified service descriptor.
         * @param descriptor the service descriptor of this capability
         * @return the builder
         */
        public static Builder<Void> of(NullaryServiceDescriptor<?> descriptor) {
            return new Builder<Void>(descriptor.getName(), false, null).setServiceType(descriptor.getType());
        }

        /**
         * Creates a builder for a dynamic capability using the name and type of the specified service descriptor.
         * @param descriptor the service descriptor of this capability
         * @return the builder
         */
        public static Builder<Void> of(UnaryServiceDescriptor<?> descriptor) {
            return new Builder<Void>(descriptor.getName(), true, null).setServiceType(descriptor.getType()).setDynamicNameMapper(UnaryCapabilityNameResolver.DEFAULT);
        }

        /**
         * Creates a builder for a dynamic capability using the name and type of the specified service descriptor.
         * @param descriptor the service descriptor of this capability
         * @return the builder
         */
        public static Builder<Void> of(BinaryServiceDescriptor<?> descriptor) {
            return new Builder<Void>(descriptor.getName(), true, null).setServiceType(descriptor.getType()).setDynamicNameMapper(BinaryCapabilityNameResolver.PARENT_CHILD);
        }

        /**
         * Creates a builder for a dynamic capability using the name and type of the specified service descriptor.
         * @param descriptor the service descriptor of this capability
         * @return the builder
         */
        public static Builder<Void> of(TernaryServiceDescriptor<?> descriptor) {
            return new Builder<Void>(descriptor.getName(), true, null).setServiceType(descriptor.getType()).setDynamicNameMapper(TernaryCapabilityNameResolver.GRANDPARENT_PARENT_CHILD);
        }

        /**
         * Creates a builder for a dynamic capability using the name and type of the specified service descriptor.
         * @param descriptor the service descriptor of this capability
         * @return the builder
         */
        public static Builder<Void> of(QuaternaryServiceDescriptor<?> descriptor) {
            return new Builder<Void>(descriptor.getName(), true, null).setServiceType(descriptor.getType()).setDynamicNameMapper(QuaternaryCapabilityNameResolver.GREATGRANDPARENT_GRANDPARENT_PARENT_CHILD);
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
         * Sets whether this capability can be registered at more than one point within the same
         * overall scope.
         * @param allowMultipleRegistrations {@code true} if the capability can legally be registered in more than
         *                                               one location in the same scope; {@code false} if an attempt
         *                                               to do this should result in an exception
         * @return the builder
         */
        public Builder<T> setAllowMultipleRegistrations(boolean allowMultipleRegistrations) {
            this.allowMultipleRegistrations = allowMultipleRegistrations;
            return this;
        }

        /*
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
         * Sets the stability level of this capability.
         * @param stability a stability level
         * @return a reference to this builder
         */
        public Builder<T> setStability(Stability stability) {
            this.stability = stability;
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
