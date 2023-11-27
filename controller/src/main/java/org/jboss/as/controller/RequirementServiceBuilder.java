/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Map;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A {@link org.jboss.msc.service.ServiceBuilder} that supports capability requirements.
 * @param <T> an ignored service value type
 * @author Paul Ferraro
 */
public interface RequirementServiceBuilder<T> extends ServiceBuilder<T> {

    @Override
    RequirementServiceBuilder<T> setInitialMode(ServiceController.Mode mode);

    @Override
    RequirementServiceBuilder<T> setInstance(Service service);

    @Override
    RequirementServiceBuilder<T> addListener(LifecycleListener listener);

    /**
     * Establishes a requirement on the specified capability.
     * @param capabilityName name of capability requirement
     * @param dependencyType the class of the value of the dependency
     * @param referenceNames dynamic part(s) of capability name, only useful when using dynamic named capabilities
     * @param <V> the type of the value of the dependency
     * @return a reference to the required dependency
     */
    <V> Supplier<V> requiresCapability(String capabilityName, Class<V> dependencyType, String... referenceNames);

    /**
     * Establishes a requirement on the service provided by the capability with the specified descriptor.
     * @param <V> the value type of the required service
     * @param descriptor a service descriptor for a capability
     * @return a supplier of the service value
     */
    default <V> Supplier<V> requires(NullaryServiceDescriptor<V> descriptor) {
        return this.requiresCapability(descriptor.getName(), descriptor.getType());
    }

    /**
     * Establishes a requirement on the service provided by the capability with the specified descriptor.
     * @param <V> the value type of the required service
     * @param descriptor a service descriptor for a capability
     * @param referenceName the dynamic component of the capability name
     * @return a supplier of the service value
     */
    default <V> Supplier<V> requires(UnaryServiceDescriptor<V> descriptor, String referenceName) {
        Map.Entry<String, String[]> resolved = descriptor.resolve(referenceName);
        return this.requiresCapability(resolved.getKey(), descriptor.getType(), resolved.getValue());
    }

    /**
     * Establishes a requirement on the service provided by the capability with the specified descriptor.
     * @param <V> the value type of the required service
     * @param descriptor a service descriptor for a capability
     * @param parentName the first dynamic component of the capability name
     * @param childName the second dynamic component of the capability name
     * @return a supplier of the service value
     */
    default <V> Supplier<V> requires(BinaryServiceDescriptor<V> descriptor, String parentName, String childName) {
        Map.Entry<String, String[]> resolved = descriptor.resolve(parentName, childName);
        return this.requiresCapability(resolved.getKey(), descriptor.getType(), resolved.getValue());
    }

    /**
     * Establishes a requirement on the service provided by the capability with the specified descriptor.
     * @param <V> the value type of the required service
     * @param descriptor a service descriptor for a capability
     * @param grandparentName the first dynamic component of the capability name
     * @param parentName the second dynamic component of the capability name
     * @param childName the third dynamic component of the capability name
     * @return a supplier of the service value
     */
    default <V> Supplier<V> requires(TernaryServiceDescriptor<V> descriptor, String grandparentName, String parentName, String childName) {
        Map.Entry<String, String[]> resolved = descriptor.resolve(grandparentName, parentName, childName);
        return this.requiresCapability(resolved.getKey(), descriptor.getType(), resolved.getValue());
    }

    /**
     * Establishes a requirement on the service provided by the capability with the specified descriptor.
     * @param <V> the value type of the required service
     * @param descriptor a service descriptor for a capability
     * @param greatGrandparentName the first dynamic component of the capability name
     * @param grandparentName the second dynamic component of the capability name
     * @param parentName the third dynamic component of the capability name
     * @param childName the fourth dynamic component of the capability name
     * @return a supplier of the service value
     */
    default <V> Supplier<V> requires(QuaternaryServiceDescriptor<V> descriptor, String greatGrandparentName, String grandparentName, String parentName, String childName) {
        Map.Entry<String, String[]> resolved = descriptor.resolve(greatGrandparentName, grandparentName, parentName, childName);
        return this.requiresCapability(resolved.getKey(), descriptor.getType(), resolved.getValue());
    }
}
