/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.capability;

import org.jboss.msc.service.ServiceName;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Resolves capability service descriptor references to a {@link ServiceName}.
 * @author Paul Ferraro
 */
public interface CapabilityServiceDescriptorResolver {

    /**
     * Resolves the {@link ServiceName} of the capability described by the specified descriptor.
     * @param <T> the service value type
     * @param descriptor the capability service descriptor
     * @return the service name
     */
    <T> ServiceName getCapabilityServiceName(NullaryServiceDescriptor<T> descriptor);

    /**
     * Resolves the {@link ServiceName} of the capability described by the specified descriptor using the specified reference name.
     * @param <T> the service value type
     * @param descriptor the capability service descriptor
     * @param name the dynamic name component
     * @return the service name
     */
    <T> ServiceName getCapabilityServiceName(UnaryServiceDescriptor<T> descriptor, String name);

    /**
     * Resolves the {@link ServiceName} of the capability described by the specified descriptor using the specified reference names.
     * @param <T> the service value type
     * @param descriptor the capability service descriptor
     * @param parent the first dynamic name component
     * @param child the second dynamic name component
     * @return the service name
     */
    <T> ServiceName getCapabilityServiceName(BinaryServiceDescriptor<T> descriptor, String parent, String child);

    /**
     * Resolves the {@link ServiceName} of the capability described by the specified descriptor using the specified reference names.
     * @param <T> the service value type
     * @param descriptor the capability service descriptor
     * @param grandparent the first dynamic name component
     * @param parent the second dynamic name component
     * @param child the third dynamic name component
     * @return the service name
     */
    <T> ServiceName getCapabilityServiceName(TernaryServiceDescriptor<T> descriptor, String grandparent, String parent, String child);

    /**
     * Resolves the {@link ServiceName} of the capability described by the specified descriptor using the specified reference names.
     * @param <T> the service value type
     * @param descriptor the capability service descriptor
     * @param greatGrandparent the first dynamic name component
     * @param grandparent the second dynamic name component
     * @param parent the third dynamic name component
     * @param child the fourth dynamic name component
     * @return the service name
     */
    <T> ServiceName getCapabilityServiceName(QuaternaryServiceDescriptor<T> descriptor, String greatGrandparent, String grandparent, String parent, String child);
}
