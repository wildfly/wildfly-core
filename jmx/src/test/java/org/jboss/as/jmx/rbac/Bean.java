/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.rbac;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

public class Bean implements BeanMBean, NotificationBroadcaster, Serializable {
    private static final long serialVersionUID = 1L;

    private final Set<NotificationListener> listeners = Collections.synchronizedSet(new HashSet<NotificationListener>());
    volatile int attr = 5;

    @Override
    public int getAttr() {
        return attr;
    }

    @Override
    public void setAttr(int i) {
        attr = i;
    }

    public void method() {

    }

    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
            throws IllegalArgumentException {
        listeners.add(listener);
    }

    @Override
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        listeners.remove(listener);
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return null;
    }
}
