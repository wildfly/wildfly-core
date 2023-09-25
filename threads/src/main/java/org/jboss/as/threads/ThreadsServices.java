/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.ServiceName;

/**
 * Utilities related to threa management services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ThreadsServices {

    private ThreadsServices() {
    }

    public static final ServiceName THREAD = ServiceName.JBOSS.append("thread");
    public static final ServiceName FACTORY = THREAD.append("factory");
    public static final ServiceName EXECUTOR = THREAD.append("executor");

    /**
     * Standard implementation of {@link HandoffExecutorResolver} -- a {@link HandoffExecutorResolver.SimpleResolver} with a base service name
     * of {@link #EXECUTOR}.
     */
    public static final HandoffExecutorResolver STANDARD_HANDOFF_EXECUTOR_RESOLVER = new HandoffExecutorResolver.SimpleResolver(ThreadsServices.EXECUTOR);

    /**
     * Standard implementation of {@link ThreadFactoryResolver} -- a {@link ThreadFactoryResolver.SimpleResolver} with a base service name
     * of {@link #EXECUTOR}.
     */
    public static final ThreadFactoryResolver STANDARD_THREAD_FACTORY_RESOLVER = new ThreadFactoryResolver.SimpleResolver(ThreadsServices.FACTORY);

    public static ThreadFactoryResolver getThreadFactoryResolver(String type) {
        if(type != null && ! type.isEmpty()) {
           return new ThreadFactoryResolver.SimpleResolver(ServiceNameFactory.parseServiceName(getCapabilityBaseName(type)));
        }
        return STANDARD_THREAD_FACTORY_RESOLVER;
    }

    public static HandoffExecutorResolver getHandoffExecutorResolver(String type) {
        if(type != null && ! type.isEmpty()) {
           return new HandoffExecutorResolver.SimpleResolver(ServiceNameFactory.parseServiceName(getCapabilityBaseName(type)));
        }
        return STANDARD_HANDOFF_EXECUTOR_RESOLVER;
    }

    public static ServiceName threadFactoryName(String name) {
        return FACTORY.append(name);
    }

    public static ServiceName executorName(final String name) {
        return EXECUTOR.append(name);
    }

    public static RuntimeCapability<Void> createCapability(String type, Class<?> serviceValueType) {
       return RuntimeCapability.Builder.of(getCapabilityBaseName(type) , true, serviceValueType).build();
    }

    public static String getCapabilityBaseName(String type) {
       assert type != null && !type.isEmpty();
       return "org.wildfly.threads.executor." + type;
    }

}
