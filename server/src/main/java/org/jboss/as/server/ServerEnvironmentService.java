/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import static org.jboss.as.server.ServerService.SERVER_ENVIRONMENT_CAPABILITY;

import java.util.function.Consumer;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Exposes the {@link ServerEnvironment} via a {@link Service}.
 * <p>
 * Services that need access to the {@code ServerEnvironment} can use this service to
 * have it injected. For example, suppose we have a service {@code MyService}
 * that has a field {@code injectedEnvironment} into which it wants the
 * ServerEnvironment injected. And suppose {@code MyService} exposes a utility method
 * to facilitate installing it via a {@link ServiceTarget}. The {@code ServerEnvironment}
 * injection could be done as follows:
 * </p>
 *
 * <pre>
 * public static void addService(BatchBuilder batchBuilder) {
 *     MyService myService = new MyService();
 *     InjectedValue<ServerEnvironment> injectedEnvironment = myService.injectedEnvironment;
 *
 *     batchBuilder.addService(MyService.SERVICE_NAME, myService)
 *                 .addSystemDependency(ServerEnvironmentService.SERVICE_NAME, ServerEnvironment.class, injectedEnvironment);
 * }
 * </pre>
 *
 * @author Brian Stansberry
 */
public class ServerEnvironmentService {

    /**
     * Standard ServiceName under which a ServerEnvironmentService would be registered
     * @deprecated Generate ServiceName via {@link org.jboss.as.server.ServerService#SERVER_ENVIRONMENT_CAPABILITY_NAME} instead.
     */
    @Deprecated
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("server", "environment");

    /**
     * Adds a ServerEnvironmentService based on the given {@code serverEnvironment}
     * to the given batch under name {@link #SERVICE_NAME}.
     *
     * @param serverEnvironment the {@code ServerEnvironment}. Cannot be {@code null}
     * @param target the batch builder. Cannot be {@code null}
     */
    public static void addService(ServerEnvironment serverEnvironment, ServiceTarget target) {
        ServiceBuilder<?> builder = target.addService();
        Consumer<ServerEnvironment> injector = builder.provides(SERVER_ENVIRONMENT_CAPABILITY.getCapabilityServiceName(), SERVICE_NAME);
        builder.setInstance(Service.newInstance(injector, serverEnvironment)).install();
    }

    private ServerEnvironmentService() {
        // Hide
    }
}
