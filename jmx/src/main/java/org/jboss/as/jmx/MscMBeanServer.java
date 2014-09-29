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

package org.jboss.as.jmx;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jboss.as.server.CurrentServiceContainer;
import org.jboss.as.server.jmx.MBeanServerPlugin;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Push any MBean into MSC as well,
 * so we can have proper dependencies between those MBeans and the ones from legacy jboss-service.xml.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class MscMBeanServer extends DelegateMBeanServerPlugin implements MBeanServerExt {
    private final ServiceContainer container;

    public MscMBeanServer(MBeanServerPlugin delegate, ServiceContainer container) {
        super(delegate);
        this.container = container;
    }

    private ServiceContainer getContainer() {
        if (container == null) {
            return CurrentServiceContainer.getServiceContainer();
        } else {
            return container;
        }
    }

    private static ServiceName toServiceName(String name) {
        return ServiceNameFactory.newServiceName(name);
    }

    private void addMBean(ObjectInstance instance) {
        addMBean(instance, true);
    }

    private void addMBean(ObjectInstance instance, boolean addAliases) {
        ServiceContainer sc = getContainer();
        if (sc == null) {
            return;
        }

        ObjectName objectName = instance.getObjectName();
        String canonicalName = objectName.getCanonicalName();
        ServiceName serviceName = toServiceName(canonicalName);
        ServiceBuilder<ObjectInstance> builder = sc.addService(serviceName, new MBeanService(instance));
        if (addAliases) {
            builder.addAliases(ServiceNameFactory.newCreateDestroy(canonicalName));
            builder.addAliases(ServiceNameFactory.newStartStop(canonicalName));
        }
        builder.setInitialMode(ServiceController.Mode.ACTIVE).install();
    }

    private void removeMBean(ObjectName name) {
        ServiceContainer sc = getContainer();
        if (sc == null) {
            return;
        }

        ServiceController<?> service = sc.getService(toServiceName(name.getCanonicalName()));
        if (service != null) {
            // handle full removal in this thread
            StabilityMonitor monitor = new StabilityMonitor();
            monitor.addController(service);
            service.setMode(ServiceController.Mode.REMOVE);
            try {
                monitor.awaitStability();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                monitor.removeController(service);
            }
        }
    }

    @Override
    public ObjectInstance registerMBeanInternal(Object mbean, ObjectName name) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        ObjectInstance instance = super.registerMBean(mbean, name);
        addMBean(instance, false);
        return instance;
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException {
        ObjectInstance instance = super.createMBean(className, name, params, signature);
        addMBean(instance);
        return instance;
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException {
        ObjectInstance instance = super.createMBean(className, name, loaderName, params, signature);
        addMBean(instance);
        return instance;
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException {
        ObjectInstance instance = super.createMBean(className, name, loaderName);
        addMBean(instance);
        return instance;
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name) throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException {
        ObjectInstance instance = super.createMBean(className, name);
        addMBean(instance);
        return instance;
    }

    @Override
    public ObjectInstance registerMBean(Object object, ObjectName name) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        ObjectInstance instance = super.registerMBean(object, name);
        addMBean(instance);
        return instance;
    }

    @Override
    public void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException {
        try {
            super.unregisterMBean(name);
        } finally {
            removeMBean(name);
        }
    }

    private static class MBeanService implements Service<ObjectInstance> {
        private ObjectInstance instance;

        private MBeanService(ObjectInstance instance) {
            this.instance = instance;
        }

        public void start(StartContext context) throws StartException {
        }

        public void stop(StopContext context) {
        }

        public ObjectInstance getValue() throws IllegalStateException, IllegalArgumentException {
            return instance;
        }
    }
}
