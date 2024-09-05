/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.msc.service.ServiceName;
import org.wildfly.service.Dependency;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Encapsulates a dependency on a {@link org.jboss.msc.Service} that supplies a value.
 * @author Paul Ferraro
 */
public interface ServiceDependency<V> extends Dependency<RequirementServiceBuilder<?>, V> {

    @Override
    default ServiceDependency<V> andThen(Consumer<? super RequirementServiceBuilder<?>> after) {
        Objects.requireNonNull(after);
        return new ServiceDependency<>() {
            @Override
            public void accept(RequirementServiceBuilder<?> builder) {
                ServiceDependency.this.accept(builder);
                after.accept(builder);
            }

            @Override
            public V get() {
                return ServiceDependency.this.get();
            }
        };
    }

    @Override
    default <R> ServiceDependency<R> map(Function<V, R> mapper) {
        Objects.requireNonNull(mapper);
        return new ServiceDependency<>() {
            @Override
            public void accept(RequirementServiceBuilder<?> builder) {
                ServiceDependency.this.accept(builder);
            }

            @Override
            public R get() {
                return mapper.apply(ServiceDependency.this.get());
            }
        };
    }

    @Override
    default <T, R> ServiceDependency<R> combine(Dependency<RequirementServiceBuilder<?>, T> dependency, BiFunction<V, T, R> mapper) {
        Objects.requireNonNull(dependency);
        Objects.requireNonNull(mapper);
        return new ServiceDependency<>() {
            @Override
            public void accept(RequirementServiceBuilder<?> builder) {
                ServiceDependency.this.accept(builder);
                dependency.accept(builder);
            }

            @Override
            public R get() {
                return mapper.apply(ServiceDependency.this.get(), dependency.get());
            }
        };
    }

    /**
     * Returns a pseudo-dependency whose {@link #get()} returns the specified value.
     * @param <T> the value type
     * @param value a service value
     * @return a service dependency
     */
    @SuppressWarnings("unchecked")
    static <T> ServiceDependency<T> of(T value) {
        return (value != null) ? new SimpleServiceDependency<>(value) : (ServiceDependency<T>) SimpleServiceDependency.NULL;
    }

    /**
     * Returns a pseudo-dependency whose {@link #get()} returns the value from the specified supplier.
     * @param <V> the value type
     * @param factory a service value supplier
     * @return a service dependency
     * @throws NullPointerException if {@code supplier} was null
     */
    static <V> ServiceDependency<V> from(Supplier<V> supplier) {
        Objects.requireNonNull(supplier);
        return new SuppliedServiceDependency<>(supplier);
    }

    /**
     * Returns a dependency on the service with the specified name.
     * @param <T> the dependency type
     * @param name a service name
     * @return a service dependency
     */
    static <T> ServiceDependency<T> on(ServiceName name) {
        return (name != null) ? new DefaultServiceDependency<>(name) : of(null);
    }

    /**
     * Wraps a {@link org.wildfly.service.ServiceDependency} as a {@link ServiceDependency}.
     * @param <T> the dependency type
     * @return a service dependency
     * @throws NullPointerException if {@code dependency} was null
     */
    static <T> ServiceDependency<T> from(org.wildfly.service.ServiceDependency<T> dependency) {
        Objects.requireNonNull(dependency);
        return new ServiceDependency<>() {
            @Override
            public void accept(RequirementServiceBuilder<?> builder) {
                dependency.accept(builder);
            }

            @Override
            public T get() {
                return dependency.get();
            }
        };
    }

    /**
     * Returns a dependency on the capability with the specified name and type, resolved against the specified references names.
     * This method is provided for migration purposes.
     * Users should prefer {@link ServiceDescriptor}-based variants of this method whenever possible.
     * @param <T> the dependency type
     * @param capabilityName the name of the referenced capability
     * @param type the service type of the referenced capability
     * @param referenceNames the reference names
     * @return a service dependency
     */
    static <T> ServiceDependency<T> on(String capabilityName, Class<T> type, String... referenceNames) {
        return new AbstractServiceDependency<>(Map.entry(capabilityName, referenceNames)) {
            @Override
            public Supplier<T> apply(RequirementServiceBuilder<?> builder) {
                return builder.requiresCapability(capabilityName, type, referenceNames);
            }
        };
    }

    /**
     * Returns a dependency on the specified capability.
     * @param <T> the dependency type
     * @param descriptor the descriptor for the required service
     * @return a service dependency
     */
    static <T> ServiceDependency<T> on(NullaryServiceDescriptor<T> descriptor) {
        return new AbstractServiceDependency<>(descriptor.resolve()) {
            @Override
            public Supplier<T> apply(RequirementServiceBuilder<?> builder) {
                return builder.requires(descriptor);
            }
        };
    }

    /**
     * Returns a dependency on the specified unary capability, resolved against the specified reference name.
     * @param <T> the dependency type
     * @param descriptor the descriptor for the required service
     * @param name the reference name
     * @return a service dependency
     */
    static <T> ServiceDependency<T> on(UnaryServiceDescriptor<T> descriptor, String name) {
        return new AbstractServiceDependency<>(descriptor.resolve(name)) {
            @Override
            public Supplier<T> apply(RequirementServiceBuilder<?> builder) {
                return builder.requires(descriptor, name);
            }
        };
    }

    /**
     * Returns a dependency on the specified binary capability, resolved against the specified reference names.
     * @param <T> the dependency type
     * @param descriptor the descriptor for the required service
     * @param parentName the parent reference name
     * @param childName the child reference name
     * @return a service dependency
     */
    static <T> ServiceDependency<T> on(BinaryServiceDescriptor<T> descriptor, String parentName, String childName) {
        return new AbstractServiceDependency<>(descriptor.resolve(parentName, childName)) {
            @Override
            public Supplier<T> apply(RequirementServiceBuilder<?> builder) {
                return builder.requires(descriptor, parentName, childName);
            }
        };
    }

    /**
     * Returns a dependency on the specified ternary capability, resolved against the specified reference names.
     * @param <T> the dependency type
     * @param descriptor the descriptor for the required service
     * @param grandparentName the grandparentName reference name
     * @param parentName the parent reference name
     * @param childName the child reference name
     * @return a service dependency
     */
    static <T> ServiceDependency<T> on(TernaryServiceDescriptor<T> descriptor, String grandparentName, String parentName, String childName) {
        return new AbstractServiceDependency<>(descriptor.resolve(grandparentName, parentName, childName)) {
            @Override
            public Supplier<T> apply(RequirementServiceBuilder<?> builder) {
                return builder.requires(descriptor, grandparentName, parentName, childName);
            }
        };
    }

    /**
     * Returns a dependency on the specified quaternary capability, resolved against the specified reference names.
     * @param <T> the dependency type
     * @param descriptor the descriptor for the required service
     * @param ancestorName the ancestor reference name
     * @param grandparentName the grandparent reference name
     * @param parentName the parent reference name
     * @param childName the child reference name
     * @return a service dependency
     */
    static <T> ServiceDependency<T> on(QuaternaryServiceDescriptor<T> descriptor, String ancestorName, String grandparentName, String parentName, String childName) {
        return new AbstractServiceDependency<>(descriptor.resolve(ancestorName, grandparentName, parentName, childName)) {
            @Override
            public Supplier<T> apply(RequirementServiceBuilder<?> builder) {
                return builder.requires(descriptor, ancestorName, grandparentName, parentName, childName);
            }
        };
    }

    abstract class AbstractServiceDependency<T> extends AbstractDependency<RequirementServiceBuilder<?>, T> implements ServiceDependency<T> {
        private final Map.Entry<String, String[]> resolved;

        AbstractServiceDependency(Map.Entry<String, String[]> resolved) {
            this.resolved = resolved;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof AbstractServiceDependency)) return false;
            Map.Entry<String, String[]> resolved = ((AbstractServiceDependency<?>) object).resolved;
            return this.resolved.getKey().equals(resolved.getKey()) && Arrays.equals(this.resolved.getValue(), resolved.getValue());
        }

        @Override
        public int hashCode() {
            int result = this.resolved.getKey().hashCode();
            for (String name : this.resolved.getValue()) {
                result = 31 * result + name.hashCode();
            }
            return result;
        }

        @Override
        public String toString() {
            ServiceName name = ServiceNameFactory.parseServiceName(this.resolved.getKey());
            return ((this.resolved.getValue().length > 0) ? name.append(this.resolved.getValue()) : name).getCanonicalName();
        }
    }

    class SimpleServiceDependency<V> extends SimpleDependency<RequirementServiceBuilder<?>, V> implements ServiceDependency<V> {
        static final ServiceDependency<Object> NULL = new SimpleServiceDependency<>(null);

        SimpleServiceDependency(V value) {
            super(value);
        }
    }

    class SuppliedServiceDependency<V> extends SuppliedDependency<RequirementServiceBuilder<?>, V> implements ServiceDependency<V> {
        SuppliedServiceDependency(Supplier<V> supplier) {
            super(supplier);
        }
    }

    class DefaultServiceDependency<V> extends Dependency.DefaultDependency<RequirementServiceBuilder<?>, V> implements ServiceDependency<V> {

        DefaultServiceDependency(ServiceName name) {
            super(name);
        }
    }
}
