/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capability;

import java.util.function.Consumer;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.wildfly.subsystem.service.AsyncServiceBuilder;

/**
 * A {@link CapabilityServiceBuilder} decorator whose installed service will start and/or stop asynchronously.
 * e.g.
 * <code><![CDATA[
 * CapabilityServiceTarget target = ...;
 * RuntimeCapability<?> capability = ...;
 * Service service = ...;
 * CapabilityServiceBuilder<?> builder = new AsyncCapabilityServiceBuilder<>(target.addCapability(capability));
 * builder.setInstance(service).install();
 * ]]></code>
 * @author Paul Ferraro
 */
public class AsyncCapabilityServiceBuilder<T> extends AsyncServiceBuilder<T> implements CapabilityServiceBuilder<T> {

    private final CapabilityServiceBuilder<T> builder;

    public AsyncCapabilityServiceBuilder(CapabilityServiceBuilder<T> builder) {
        super(builder);
        this.builder = builder;
    }

    public AsyncCapabilityServiceBuilder(CapabilityServiceBuilder<T> builder, Async async) {
        super(builder, async);
        this.builder = builder;
    }

    @Override
    public CapabilityServiceBuilder<T> addListener(LifecycleListener listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public CapabilityServiceBuilder<T> setInitialMode(Mode mode) {
        super.setInitialMode(mode);
        return this;
    }

    @Override
    public CapabilityServiceBuilder<T> setInstance(Service service) {
        super.setInstance(service);
        return this;
    }

    @Override
    public <V> Consumer<V> provides(RuntimeCapability<?> capability) {
        return this.builder.provides(capability);
    }

    @Override
    public <V> Consumer<V> provides(RuntimeCapability<?>... capabilities) {
        return this.builder.provides(capabilities);
    }

    @Override
    public <V> Consumer<V> provides(RuntimeCapability<?> capability, ServiceName alias, ServiceName... aliases) {
        return this.builder.provides(capability, alias, aliases);
    }

    @Override
    public <V> Consumer<V> provides(RuntimeCapability<?>[] capabilities, ServiceName[] aliases) {
        return this.builder.provides(capabilities, aliases);
    }
}
