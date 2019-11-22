/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * A holder class for constants containing the names of the core services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class Services {

    private Services() {
    }

    /**
     * The service name of the root application server service.
     */
    public static final ServiceName JBOSS_AS = ServiceName.JBOSS.append("as");

    /**
     * The service corresponding to the {@link org.jboss.as.controller.ModelController} for this instance.
     */
    public static final ServiceName JBOSS_SERVER_CONTROLLER = JBOSS_AS.append("server-controller");

    /**
     * The service corresponding to the {@link java.util.concurrent.ExecutorService} for this instance.
     *
     * @deprecated use capability org.wildfly.management.executor
     */
    @Deprecated
    public static final ServiceName JBOSS_SERVER_EXECUTOR = JBOSS_AS.append("server-executor");

    /**
     * The service corresponding to the {@link org.jboss.as.server.moduleservice.ServiceModuleLoader} for this instance.
     */
    public static final ServiceName JBOSS_SERVICE_MODULE_LOADER = JBOSS_AS.append("service-module-loader");

    /**
     * The service corresponding to the {@link org.jboss.as.server.moduleservice.ExternalModuleService} for this instance.
     *
     * @deprecated use capability org.wildfly.management.external-module
     */
    @Deprecated
    public static final ServiceName JBOSS_EXTERNAL_MODULE_SERVICE = JBOSS_AS.append("external-module-service");

    public static final ServiceName JBOSS_PRODUCT_CONFIG_SERVICE = JBOSS_AS.append("product-config");

    public static final ServiceName JBOSS_SUSPEND_CONTROLLER = ServerService.SUSPEND_CONTROLLER_CAPABILITY.getCapabilityServiceName();

    /**
     * Creates dependency on management executor.
     *
     * @param builder the builder
     * @param injector the injector
     * @param <T> the parameter type
     * @return service builder instance
     * @deprecated Use {@link #requireServerExecutor(ServiceBuilder)} instead. This method will be removed in the future.
     */
    @Deprecated
    public static <T> ServiceBuilder<T> addServerExecutorDependency(ServiceBuilder<T> builder, Injector<ExecutorService> injector) {
        return builder.addDependency(ServerService.MANAGEMENT_EXECUTOR, ExecutorService.class, injector);
    }

    /**
     * Creates dependency on management executor and returns supplier providing it.
     *
     * @param builder the builder to use for requirement
     * @return supplier providing server executor
     */
    public static Supplier<ExecutorService> requireServerExecutor(final ServiceBuilder<?> builder) {
        return builder.requires(ServerService.MANAGEMENT_EXECUTOR);
    }

}
