/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.jmx;

import javax.management.MBeanServer;

/**
 * Interface for the pluggable mbean server set up by the jmx subsystem
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface PluggableMBeanServer extends MBeanServer {

    void addPlugin(MBeanServerPlugin delegate);

    void removePlugin(MBeanServerPlugin delegate);

}
