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
 *
 * @author Brian Stansberry
 */
public class ServerEnvironmentService {

    /**
     * Standard ServiceName under which a ServerEnvironmentService would be registered
     * @deprecated Generate ServiceName via {@link ServerEnvironment#SERVICE_DESCRIPTOR} instead.
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
