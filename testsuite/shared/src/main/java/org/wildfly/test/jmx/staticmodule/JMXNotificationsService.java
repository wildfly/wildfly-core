/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.jmx.staticmodule;


import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServer;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * ServiceActivator that installs itself as a service and sets a set of system properties read from a
 * "service-activator-deployment.properties" resource in the deployment. If no resource is available, sets a default
 * property.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class JMXNotificationsService implements ServiceActivator, Service<Void> {

    public static final ServiceName MBEAN_SERVER_SERVICE_NAME = ServiceName.JBOSS.append("mbean", "server");
    public static final ServiceName SERVICE_NAME = ServiceName.of("test", "deployment", "jmx");
    public static final String PROPERTIES_RESOURCE = "service-activator-deployment.properties";
    public static final String MBEAN_CLASS_NAME = "mbean.class.name";
    public static final String MBEAN_OBJECT_NAME = "mbean.object.name";
    public static final String LISTENER_OBJECT_NAME = "listener.object.name";
    public static final String LISTENER_CLASS_NAME = "listener.class.name";

    MBeanServer mbeanServerValue = ManagementFactory.getPlatformMBeanServer();

    private ObjectName name = null;
    private ObjectName targetName = null;
    private NotificationListener listener = null;

    /**
     * When testing state notifications for a listener in a static module,
     * we want to keep the listener active all the way until shutdown of the server
     * to be able to receive the final stopping->stopped notification.
     * This flag, when true, means that when this MSC service is removed,
     * the listener should NOT be unregistered with it.
     */
    private boolean keepAtStop = false;

    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        serviceActivatorContext.getServiceTarget().addService(SERVICE_NAME, this)
                .install();
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
            if (properties.containsKey(MBEAN_OBJECT_NAME) && properties.containsKey(MBEAN_CLASS_NAME)) {
                name = new ObjectName(properties.getProperty(MBEAN_OBJECT_NAME));
                Class mbeanClass = Class.forName(properties.getProperty(MBEAN_CLASS_NAME));
                registerMBean(mbeanClass.newInstance(), name);
            }
            if (properties.containsKey(LISTENER_OBJECT_NAME) && properties.containsKey(LISTENER_CLASS_NAME)) {
                Class listenerClass = Class.forName(properties.getProperty(LISTENER_CLASS_NAME));
                if (NotificationListener.class.isAssignableFrom(listenerClass)) {
                    targetName = new ObjectName(properties.getProperty(LISTENER_OBJECT_NAME));
                    listener = (NotificationListener) listenerClass.newInstance();
                    addNotificationListener(targetName, listener);
                }
            }
            if(Boolean.valueOf((String)properties.getOrDefault("keep.after.stop", "false"))) {
                keepAtStop = true;
            }
        } catch (Exception e) {
            throw new StartException(e);
        }
    }

    @Override
    public void stop(StopContext context) {
        if(name != null) {
            try {
                unregisterMBean(name);
            } catch (Exception ex) {
                Logger.getLogger(JMXNotificationsService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if(targetName != null && listener != null && !keepAtStop) {
           try {
                removeNotificationListener(targetName, listener);
            } catch (Exception ex) {
                Logger.getLogger(JMXNotificationsService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    private ObjectInstance registerMBean(Object mbean, ObjectName name) throws Exception {
        return mbeanServerValue.registerMBean(mbean, name);
    }

    private void unregisterMBean(ObjectName name) throws Exception {
        mbeanServerValue.unregisterMBean(name);
    }

    private void addNotificationListener(ObjectName name, NotificationListener listener) throws Exception {
        mbeanServerValue.addNotificationListener(name, listener, null, null);
    }

    private void removeNotificationListener(ObjectName name, NotificationListener listener) throws Exception {
        mbeanServerValue.removeNotificationListener(name, listener);
    }

}
