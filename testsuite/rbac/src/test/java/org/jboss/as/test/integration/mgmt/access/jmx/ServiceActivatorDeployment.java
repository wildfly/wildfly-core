/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.mgmt.access.jmx;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.server.jmx.PluggableMBeanServer;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * ServiceActivator that installs itself as a service and sets a set of system properties read from a
 * "service-activator-deployment.properties" resource in the deployment. If no resource is available, sets a default
 * property.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ServiceActivatorDeployment implements ServiceActivator, Service<Void> {

    public static final ServiceName SERVICE_NAME = ServiceName.of("test", "deployment", "jmx");
    public static final String PROPERTIES_RESOURCE = "service-activator-deployment.properties";
    public static final String MBEAN_CLASS_NAME = "mbean.class.name";
    public static final String MBEAN_OBJECT_NAME = "mbean.object.name";

    InjectedValue<PluggableMBeanServer> mbeanServerValue = new InjectedValue<PluggableMBeanServer>();

    private ObjectName name = null;

    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        serviceActivatorContext.getServiceTarget().addService(SERVICE_NAME, this)
                .addDependency(MBeanServerService.SERVICE_NAME, PluggableMBeanServer.class, mbeanServerValue).install();
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        InputStream is = getClass().getResourceAsStream("/" + PROPERTIES_RESOURCE);
        Properties properties = new Properties();
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
        }
        try {
            name =  new ObjectName(properties.getProperty(MBEAN_OBJECT_NAME));
            Class mbeanClass = Class.forName(properties.getProperty(MBEAN_CLASS_NAME));
            mbeanServerValue.getValue().registerMBean(mbeanClass.newInstance(),name);
        } catch (InstanceAlreadyExistsException e) {
            throw new StartException(e);
        } catch (MBeanException e) {
            throw new StartException(e);
        } catch (NotCompliantMBeanException e) {
            throw new StartException(e);
        } catch (MalformedObjectNameException e) {
           throw new StartException(e);
        } catch (ClassNotFoundException e) {
           throw new StartException(e);
        } catch (InstantiationException e) {
           throw new StartException(e);
        } catch (IllegalAccessException e) {
           throw new StartException(e);
        }
    }

    @Override
    public void stop(StopContext context) {
        if(name != null) {
            try {
                mbeanServerValue.getValue().unregisterMBean(name);
            } catch (InstanceNotFoundException ex) {
                Logger.getLogger(ServiceActivatorDeployment.class.getName()).log(Level.SEVERE, null, ex);
            } catch (MBeanRegistrationException ex) {
                Logger.getLogger(ServiceActivatorDeployment.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }
}
