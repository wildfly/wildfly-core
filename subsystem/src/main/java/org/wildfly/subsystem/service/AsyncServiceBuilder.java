/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;

/**
 * A {@link ServiceBuilder} decorator whose installed service will start and/or stop asynchronously.
 * e.g.
 * <code><![CDATA[
 * ServiceTarget target = ...;
 * ServiceName name = ...;
 * Service service = ...;
 * ServiceBuilder<?> builder = new AsyncServiceBuilder<>(target.addService(name));
 * builder.setInstance(service).install();
 * ]]></code>
 * @author Paul Ferraro
 */
public class AsyncServiceBuilder<T> extends org.wildfly.service.AsyncServiceBuilder<T> implements RequirementServiceBuilder<T> {

    private final RequirementServiceBuilder<T> builder;

    public AsyncServiceBuilder(RequirementServiceBuilder<T> builder) {
        this(builder, Async.START_AND_STOP);
    }

    public AsyncServiceBuilder(RequirementServiceBuilder<T> builder, Async async) {
        super(builder, builder.requiresCapability(AbstractControllerService.EXECUTOR_CAPABILITY.getName(), Executor.class), async);
        this.builder = builder;
    }

    @Override
    public RequirementServiceBuilder<T> addListener(LifecycleListener listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public RequirementServiceBuilder<T> setInitialMode(Mode mode) {
        super.setInitialMode(mode);
        return this;
    }

    @Override
    public RequirementServiceBuilder<T> setInstance(Service service) {
        super.setInstance(service);
        return this;
    }

    @Override
    public <V> Supplier<V> requiresCapability(String capabilityName, Class<V> dependencyType, String... referenceNames) {
        return this.builder.requiresCapability(capabilityName, dependencyType, referenceNames);
    }
}
