/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.jmx;

import java.io.ObjectInputStream;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;

/**
 * A wrapper around {@link MBeanServer} to switch on authorization for all calls.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class AuthorizingMBeanServer implements MBeanServer {

    private static final ThreadLocal<Boolean> AUTHORIZING = new ThreadLocal<Boolean>() {

        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }

    };

    private final MBeanServer delegate;

    private AuthorizingMBeanServer(final MBeanServer delegate) {
        this.delegate = delegate;
    }

    public static MBeanServer wrap(final MBeanServer delegate) {
        return new AuthorizingMBeanServer(delegate);
    }

    static boolean isAuthorizing() {
        return AUTHORIZING.get();
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name) throws ReflectionException,
            InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.createMBean(className, name);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName)
            throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.createMBean(className, name, loaderName);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature)
            throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.createMBean(className, name, params, signature);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params,
            String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException,
            MBeanException, NotCompliantMBeanException, InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.createMBean(className, name, loaderName, params, signature);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public ObjectInstance registerMBean(Object object, ObjectName name)
            throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.registerMBean(object, name);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            delegate.unregisterMBean(name);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getObjectInstance(name);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.queryMBeans(name, query);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.queryNames(name, query);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public boolean isRegistered(ObjectName name) {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.isRegistered(name);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Integer getMBeanCount() {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getMBeanCount();
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Object getAttribute(ObjectName name, String attribute)
            throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getAttribute(name, attribute);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public AttributeList getAttributes(ObjectName name, String[] attributes)
            throws InstanceNotFoundException, ReflectionException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getAttributes(name, attributes);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public void setAttribute(ObjectName name, Attribute attribute) throws InstanceNotFoundException, AttributeNotFoundException,
            InvalidAttributeValueException, MBeanException, ReflectionException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            delegate.setAttribute(name, attribute);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public AttributeList setAttributes(ObjectName name, AttributeList attributes)
            throws InstanceNotFoundException, ReflectionException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.setAttributes(name, attributes);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
            throws InstanceNotFoundException, MBeanException, ReflectionException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.invoke(name, operationName, params, signature);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public String getDefaultDomain() {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getDefaultDomain();
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public String[] getDomains() {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getDomains();
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
            Object handback) throws InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            delegate.addNotificationListener(name, listener, filter, handback);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback)
            throws InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            delegate.addNotificationListener(name, listener, filter, handback);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener)
            throws InstanceNotFoundException, ListenerNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            delegate.removeNotificationListener(name, listener);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            delegate.removeNotificationListener(name, listener, filter, handback);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name, NotificationListener listener)
            throws InstanceNotFoundException, ListenerNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            delegate.removeNotificationListener(name, listener);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
            Object handback) throws InstanceNotFoundException, ListenerNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            delegate.removeNotificationListener(name, listener, filter, handback);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public MBeanInfo getMBeanInfo(ObjectName name)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getMBeanInfo(name);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.isInstanceOf(name, className);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Object instantiate(String className) throws ReflectionException, MBeanException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.instantiate(className);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Object instantiate(String className, ObjectName loaderName)
            throws ReflectionException, MBeanException, InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.instantiate(className, loaderName);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Object instantiate(String className, Object[] params, String[] signature)
            throws ReflectionException, MBeanException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.instantiate(className, params, signature);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public Object instantiate(String className, ObjectName loaderName, Object[] params, String[] signature)
            throws ReflectionException, MBeanException, InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.instantiate(className, loaderName, params, signature);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public ObjectInputStream deserialize(ObjectName name, byte[] data) throws InstanceNotFoundException, OperationsException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.deserialize(name, data);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public ObjectInputStream deserialize(String className, byte[] data) throws OperationsException, ReflectionException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.deserialize(className, data);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public ObjectInputStream deserialize(String className, ObjectName loaderName, byte[] data)
            throws InstanceNotFoundException, OperationsException, ReflectionException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.deserialize(className, loaderName, data);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public ClassLoader getClassLoaderFor(ObjectName mbeanName) throws InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getClassLoaderFor(mbeanName);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public ClassLoader getClassLoader(ObjectName loaderName) throws InstanceNotFoundException {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getClassLoader(loaderName);
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

    @Override
    public ClassLoaderRepository getClassLoaderRepository() {
        Boolean originalValue = AUTHORIZING.get();
        try {
            AUTHORIZING.set(Boolean.TRUE);
            return delegate.getClassLoaderRepository();
        } finally {
            AUTHORIZING.set(originalValue);
        }
    }

}
