/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import io.undertow.server.ListenerRegistry;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

import java.util.function.Consumer;

/**
 * Service that maintains a registry of all Undertow listeners, and the services that are registered on them.
 *
 * TODO: not sure if this really belongs here conceptually, but in practice it is only used to match upgrade handlers with listeners
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class HttpListenerRegistryService implements Service<ListenerRegistry> {

    @Deprecated
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("http", "listener", "registry");

    private final Consumer<ListenerRegistry> serviceConsumer;

    @Deprecated
    public HttpListenerRegistryService() {
        this(null);
    }

    public HttpListenerRegistryService(final Consumer<ListenerRegistry> serviceConsumer) {
        this.serviceConsumer = serviceConsumer;
    }

    public static void install(final ServiceTarget serviceTarget) {
        final ServiceBuilder<?> builder = serviceTarget.addService(RemotingServices.HTTP_LISTENER_REGISTRY);
        final Consumer<ListenerRegistry> serviceConsumer = builder.provides(RemotingServices.HTTP_LISTENER_REGISTRY, SERVICE_NAME);
        builder.setInstance(new HttpListenerRegistryService(serviceConsumer));
        builder.install();
    }

    private volatile ListenerRegistry listenerRegistry;

    @Override
    public void start(final StartContext context) {
        listenerRegistry = new ListenerRegistry();
        if (serviceConsumer != null) {
            serviceConsumer.accept(listenerRegistry);
        }
    }

    @Override
    public void stop(final StopContext context) {
        listenerRegistry = null;
        if (serviceConsumer != null) {
            serviceConsumer.accept(null);
        }
    }

    @Override
    public ListenerRegistry getValue() throws IllegalStateException, IllegalArgumentException {
        return listenerRegistry;
    }

}
