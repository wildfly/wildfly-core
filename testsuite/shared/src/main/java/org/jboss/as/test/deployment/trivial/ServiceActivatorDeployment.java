/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.deployment.trivial;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * ServiceActivator that installs itself as a service and sets a set of system
 * properties read from a "service-activator-deployment.properties" resource in the deployment.
 * If no resource is available, sets a default property.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ServiceActivatorDeployment implements ServiceActivator, Service<Void> {

    public static final ServiceName SERVICE_NAME = ServiceName.of("test", "deployment", "trivial");
    public static final String PROPERTIES_RESOURCE = "service-activator-deployment.properties";
    public static final String DEFAULT_SYS_PROP_NAME = "test.deployment.trivial.prop";
    public static final String DEFAULT_SYS_PROP_VALUE = "default-value";

    private final Properties properties = new Properties();
    private final ServiceName serviceName;
    private final String propertiesResource;

    public ServiceActivatorDeployment() {
        this(SERVICE_NAME, PROPERTIES_RESOURCE);
    }

    public ServiceActivatorDeployment(ServiceName serviceName, String propertiesResource) {
        this.serviceName = serviceName;
        this.propertiesResource = propertiesResource;
    }

    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        serviceActivatorContext.getServiceTarget().addService(serviceName, this).install();
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        InputStream is = getClass().getResourceAsStream("/" + propertiesResource);
        if (is != null) {
            try {
                System.out.println("Properties found");
                properties.load(is);
            } catch (IOException e) {
                throw new StartException(e);
            } finally {
                try {
                    is.close();
                } catch (IOException ignored) {
                    //
                }

            }
        } else {
            properties.setProperty(DEFAULT_SYS_PROP_NAME, DEFAULT_SYS_PROP_VALUE);
        }
        for (String name : properties.stringPropertyNames()) {
            System.setProperty(name, properties.getProperty(name));
            System.out.println("Setting "+ name + " to " + properties.getProperty(name));
        }
    }

    @Override
    public void stop(StopContext context) {
        for (String name : properties.stringPropertyNames()) {
            System.clearProperty(name);
        }
        properties.clear();
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }
}
