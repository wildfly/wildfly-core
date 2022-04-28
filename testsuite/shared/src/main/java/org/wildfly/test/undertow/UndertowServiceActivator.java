/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.wildfly.test.undertow;

import java.net.SocketPermission;
import java.security.Permission;
import java.util.Arrays;
import java.util.PropertyPermission;

import javax.management.MBeanPermission;
import javax.management.MBeanServerPermission;
import javax.management.ObjectName;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;

/**
 * A default service activator which will create an Undertow service and send a default response to 0.0.0.0 port
 * 8080.
 * <p/>
 * You can override the address with the {@code jboss.bind.address} or {@code management.address} system properties.
 * <p/>
 * To override  the port use the {@code jboss.http.port} system property.
 * <p/>
 * Override any of the getter methods to customize the desired behavior.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class UndertowServiceActivator implements ServiceActivator {

    /**
     * Class dependencies required to use the {@link org.wildfly.test.undertow.UndertowService}.
     */
    public static final Class<?>[] DEPENDENCIES = {
            UndertowService.class,
            UndertowServiceActivator.class,
            TestSuiteEnvironment.class
    };

    /**
     * The default permissions needed for this activator when running under a security manager.
     * <p>
     * Example usage:
     * <pre>
     * {@code archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(UndertowServiceActivator.DEFAULT_PERMISSIONS), "permissions.xml");}
     * </pre>
     * </p>
     */
    public static final Permission[] DEFAULT_PERMISSIONS = {
            new PropertyPermission("jboss.bind.address", "read"),
            new PropertyPermission("jboss.http.port", "read"),
            new RuntimePermission("createXnioWorker"),
            new SocketPermission("*", "listen,accept,connect"),
            new MBeanServerPermission("createMBeanServer"),
            new MBeanPermission("org.jboss.threads.EnhancedQueueExecutor$MXBeanImpl", "*", ObjectName.WILDCARD, "registerMBean,unregisterMBean"),
            new MBeanPermission("org.xnio.nio.NioXnioWorker$NioWorkerMetrics", "*", ObjectName.WILDCARD, "registerMBean,unregisterMBean"),
            new MBeanPermission("org.xnio.nio.NioTcpServer$1","*", ObjectName.WILDCARD,  "registerMBean,unregisterMBean"),
    };

    public static final String DEFAULT_RESPONSE = "Response sent";

    /**
     * Appends the passed in permissions to the {@linkplain #DEFAULT_PERMISSIONS default permissions}.
     *
     * @param additionalPermissions the additional parameters to add
     *
     * @return the combined permissions
     */
    public static Permission[] appendPermissions(final Permission... additionalPermissions) {
        final Permission[] permissions;
        if (additionalPermissions == null || additionalPermissions.length == 0) {
            permissions = DEFAULT_PERMISSIONS;
        } else {
            permissions = Arrays.copyOf(DEFAULT_PERMISSIONS, DEFAULT_PERMISSIONS.length + additionalPermissions.length);
            System.arraycopy(additionalPermissions, 0, permissions, DEFAULT_PERMISSIONS.length, additionalPermissions.length);
        }
        return permissions;
    }

    private static final HttpHandler DEFAULT_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send(DEFAULT_RESPONSE);
        }
    };

    @Override
    public final void activate(final ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        final String address = getAddress();
        assert address != null : "address cannot be null";
        final int port = getPort();
        assert port > 0 : "port must be greater than 0";
        final HttpHandler handler = getHttpHandler();
        assert handler != null : "A handler is required";
        ServiceBuilder<?> builder = serviceActivatorContext.getServiceTarget().addService(getServiceName());
        createService(address, port, handler, builder);
        builder.install();
    }

    /**
     * Create the Undertow service
     *
     */
    protected void createService(String address, int port, HttpHandler handler, ServiceBuilder<?> builder) {
        UndertowService service =  new UndertowService(address, port, handler);
        builder.setInstance(service);

    }

    /**
     * Returns the service name to use when adding the UndertowService.
     *
     * @return the undertow service name
     */
    protected ServiceName getServiceName() {
        return UndertowService.DEFAULT_SERVICE_NAME;
    }

    /**
     * Returns the {@link io.undertow.server.HttpHandler handler} used to process the request
     *
     * @return the handler to use
     */
    protected HttpHandler getHttpHandler() {
        return DEFAULT_HANDLER;
    }

    /**
     * Returns the address for Undertow to bind to.
     *
     * @return the address
     */
    protected String getAddress() {
        return TestSuiteEnvironment.getHttpAddress();
    }

    /**
     * Returns the port for Undertow to bind to.
     *
     * @return the port
     */
    protected int getPort() {
        return TestSuiteEnvironment.getHttpPort();
    }
}
