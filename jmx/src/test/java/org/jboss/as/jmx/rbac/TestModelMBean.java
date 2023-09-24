/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.rbac;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeChangeNotification;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBean;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestModelMBean implements ModelMBean {

    // WFCORE-1039 hold a ref to listeners so we avoid weird test bugs when the platform MBeanServer gcs unreferenced ones
    private final Map<String, Set<NotificationListener>> listeners = new HashMap<>();

    public TestModelMBean() {

    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        throw new AttributeNotFoundException();
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException,
            MBeanException, ReflectionException {
        throw new AttributeNotFoundException();
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        return new AttributeList();
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        return null;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        return null;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        try {
            ModelMBeanAttributeInfo[] attributes = new ModelMBeanAttributeInfo[0];
            ModelMBeanConstructorInfo[] constructors = new ModelMBeanConstructorInfo[] {
                    new ModelMBeanConstructorInfo("-", this.getClass().getConstructor())
            };
            ModelMBeanOperationInfo[] operations = new ModelMBeanOperationInfo[] {
                    new ModelMBeanOperationInfo("info", "-", new MBeanParameterInfo[0], Void.class.getName(), ModelMBeanOperationInfo.INFO),
                    new ModelMBeanOperationInfo("action", "-", new MBeanParameterInfo[0], Void.class.getName(), ModelMBeanOperationInfo.ACTION),
                    new ModelMBeanOperationInfo("actionInfo", "-", new MBeanParameterInfo[0], Void.class.getName(), ModelMBeanOperationInfo.ACTION_INFO),
                    new ModelMBeanOperationInfo("unknown", "-", new MBeanParameterInfo[0], Void.class.getName(), ModelMBeanOperationInfo.UNKNOWN)
            };
            ModelMBeanNotificationInfo[] notifications = new ModelMBeanNotificationInfo[0];
            return new ModelMBeanInfoSupport(this.getClass().getName(), "-", attributes, constructors, operations, notifications);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void load() throws MBeanException, RuntimeOperationsException, InstanceNotFoundException {
    }

    @Override
    public void store() throws MBeanException, RuntimeOperationsException, InstanceNotFoundException {
    }

    @Override
    public void sendNotification(Notification ntfyObj) throws MBeanException, RuntimeOperationsException {
    }

    @Override
    public void sendNotification(String ntfyText) throws MBeanException, RuntimeOperationsException {
    }

    @Override
    public void sendAttributeChangeNotification(AttributeChangeNotification notification) throws MBeanException,
            RuntimeOperationsException {
    }

    @Override
    public void sendAttributeChangeNotification(Attribute oldValue, Attribute newValue) throws MBeanException,
            RuntimeOperationsException {
    }

    @Override
    public void addAttributeChangeNotificationListener(NotificationListener listener, String attributeName, Object handback)
            throws MBeanException, RuntimeOperationsException, IllegalArgumentException {
        addListener(listener, attributeName);
    }

    @Override
    public void removeAttributeChangeNotificationListener(NotificationListener listener, String attributeName)
            throws MBeanException, RuntimeOperationsException, ListenerNotFoundException {
        removeListener(listener, attributeName);
    }

    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
            throws IllegalArgumentException {
        addListener(listener, null);
    }

    @Override
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        removeListener(listener, null);
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return null;
    }

    @Override
    public void setModelMBeanInfo(ModelMBeanInfo inModelMBeanInfo) throws MBeanException, RuntimeOperationsException {
    }

    @Override
    public void setManagedResource(Object mr, String mr_type) throws MBeanException, RuntimeOperationsException,
            InstanceNotFoundException, InvalidTargetObjectTypeException {
    }

    private void addListener(NotificationListener listener, String attribute) {
        synchronized (listeners) {
            Set<NotificationListener> set = listeners.get(attribute);
            if (set == null) {
                set = new HashSet<>();
                listeners.put(attribute, set);
            }
            set.add(listener);
        }
    }

    private void removeListener(NotificationListener listener, String attribute) {
        synchronized (listeners) {
            Set<NotificationListener> set = listeners.get(attribute);
            if (set != null) {
                set.remove(listener);
                if (set.size() == 0) {
                    listeners.remove(attribute);
                }
            }
        }

    }
}
