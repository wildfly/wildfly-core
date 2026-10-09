/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.service.capture;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.msc.Service;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StabilityMonitor;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A mock {@link RequirementServiceBuilder} used to resolve {@link ServiceName} from a service dependency.
 * @author Paul Ferraro
 */
class ServiceNameCapturingServiceBuilder implements RequirementServiceBuilder<Object> {

    private final AtomicReference<ServiceName> reference;

    ServiceNameCapturingServiceBuilder(AtomicReference<ServiceName> reference) {
        this.reference = reference;
    }

    @Override
    public <V> Supplier<V> requires(ServiceName name) {
        this.reference.setPlain(name);
        return null;
    }

    @Override
    public <V> Supplier<V> requires(NullaryServiceDescriptor<V> descriptor) {
        this.reference.setPlain(ServiceNameFactory.resolveServiceName(descriptor));
        return null;
    }

    @Override
    public <V> Supplier<V> requires(UnaryServiceDescriptor<V> descriptor, String referenceName) {
        this.reference.setPlain(ServiceNameFactory.resolveServiceName(descriptor, referenceName));
        return null;
    }

    @Override
    public <V> Supplier<V> requires(BinaryServiceDescriptor<V> descriptor, String parentName, String childName) {
        this.reference.setPlain(ServiceNameFactory.resolveServiceName(descriptor, parentName, childName));
        return null;
    }

    @Override
    public <V> Supplier<V> requires(TernaryServiceDescriptor<V> descriptor, String grandparentName, String parentName, String childName) {
        this.reference.setPlain(ServiceNameFactory.resolveServiceName(descriptor, grandparentName, parentName, childName));
        return null;
    }

    @Override
    public <V> Supplier<V> requires(QuaternaryServiceDescriptor<V> descriptor, String greatGrandparentName, String grandparentName, String parentName, String childName) {
        this.reference.setPlain(ServiceNameFactory.resolveServiceName(descriptor, greatGrandparentName, grandparentName, parentName, childName));
        return null;
    }

    @Override
    public <V> Supplier<V> requiresCapability(String capabilityName, Class<V> dependencyType, String... referenceNames) {
        this.reference.setPlain(ServiceNameFactory.parseServiceName(capabilityName).append(referenceNames));
        return null;
    }

    @Override
    public <V> Consumer<V> provides(ServiceName... names) {
        return null;
    }

    @Override
    public ServiceController<Object> install() {
        return null;
    }

    @Override
    public ServiceBuilder<Object> addAliases(ServiceName... aliases) {
        return null;
    }

    @Deprecated
    @Override
    public <I> ServiceBuilder<Object> addDependency(ServiceName dependency, Class<I> type, Injector<I> target) {
        return this;
    }

    @Deprecated
    @Override
    public ServiceBuilder<Object> addMonitor(StabilityMonitor monitor) {
        return this;
    }

    @Override
    public RequirementServiceBuilder<Object> setInitialMode(Mode mode) {
        return this;
    }

    @Override
    public RequirementServiceBuilder<Object> setInstance(Service service) {
        return this;
    }

    @Override
    public RequirementServiceBuilder<Object> addListener(LifecycleListener listener) {
        return this;
    }
}
