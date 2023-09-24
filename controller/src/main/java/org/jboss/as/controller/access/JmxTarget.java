/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access;

import javax.management.ObjectName;

/**
 * The JMX object that is the target of an action for which access control is needed.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class JmxTarget {

    private final String method;
    private final ObjectName objectName;
    private final boolean isNonFacadeMBeansSensitive;
    private final HostEffect hostEffect;
    private final ServerGroupEffect serverGroupEffect;

    public JmxTarget(String method, ObjectName objectName, boolean isNonFacadeMBeansSensitive) {
        this(method, objectName, isNonFacadeMBeansSensitive, null, null);
    }

    public JmxTarget(String method, ObjectName objectName, boolean isNonFacadeMBeansSensitive,
                     HostEffect hostEffect, ServerGroupEffect serverGroupEffect) {
        this.method = method;
        this.objectName = objectName;
        this.isNonFacadeMBeansSensitive = isNonFacadeMBeansSensitive;
        this.hostEffect = hostEffect;
        this.serverGroupEffect = serverGroupEffect;
    }

    public String getMethod() {
        return method;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public boolean isNonFacadeMBeansSensitive() {
        return isNonFacadeMBeansSensitive;
    }

    public ServerGroupEffect getServerGroupEffect() {
        return serverGroupEffect;
    }

    public HostEffect getHostEffect() {
        return hostEffect;
    }

}
