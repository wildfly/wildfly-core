/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.jmx;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Plugin for the {@link PluggableMBeanServer}
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface MBeanServerPlugin extends MBeanServer {
    /**
     * Return {@code true} if this plugin can handle mbeans with the passed in name
     *
     * @param objectName the name of the mbean to check
     * @return whether or not this plugin can handle the mbean
     */
    boolean accepts(ObjectName objectName);

    /**
     * Return {@code true} if this plugin should audit log
     *
     * @return whether or not this plugin should audit log
     */
    boolean shouldAuditLog();

    /**
     * Return {@code true} if this plugin should authorize calls
     *
     * @return whether or not this plugin should audit log
     */
    boolean shouldAuthorize();
}