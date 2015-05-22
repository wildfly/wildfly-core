/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
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
