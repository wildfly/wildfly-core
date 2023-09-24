/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import static org.jboss.as.server.ServerService.SERVER_ENVIRONMENT_CAPABILITY;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

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
public class ServerEnvironmentService implements Service<ServerEnvironment> {


    /** Standard ServiceName under which a ServerEnvironmentService would be registered */
    /** @deprecated Use {@link #SERVER_ENVIRONMENT_CAPABILITY.getCapabilityServiceName() instead} */
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
        target.addService(SERVER_ENVIRONMENT_CAPABILITY.getCapabilityServiceName(), new ServerEnvironmentService(serverEnvironment))
                .addAliases(SERVICE_NAME)
                .install();
    }

    private final ServerEnvironment serverEnvironment;

    /**
     * Creates a ServerEnvironmentService that uses the given {@code serverEnvironment}
     * as its {@link #getValue() value}.
     *
     * @param serverEnvironment the {@code ServerEnvironment}. Cannot be {@code null}
     */
    ServerEnvironmentService(ServerEnvironment serverEnvironment) {
        assert serverEnvironment != null : "serverEnvironment is null";
        this.serverEnvironment = serverEnvironment;
    }

    @Override
    public void start(StartContext context) throws StartException {
        // no-op
    }

    @Override
    public void stop(StopContext context) {
        // no-op
    }

    @Override
    public ServerEnvironment getValue() throws IllegalStateException {
        return serverEnvironment;
    }

}
