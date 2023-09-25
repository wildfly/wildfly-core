/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import javax.management.MBeanServer;
import javax.management.MBeanServerBuilder;
import javax.management.MBeanServerDelegate;

/**
 * <p> This builder returns an instance of {@link PluggableMBeanServerImpl} which can be used to set the MBeanServer chain,
 * meaning that the platform mbean server gets the extra functionality for TCCL, ModelController and whatever other behaviour
 * we want to add.</p>
 *
 * <p> Main JBM module {@code org.jboss.modules.Main} loads services from {@code org.jboss.as.jmx} module, which includes
 * {@code META-INF/services/javax.management.MBeanServerBuilder} where the {@code PluggableMBeanServerBuilder} is specified.</p>
 *
 * <p>If the {@code PluggableMBeanServerBuilder} in the {@code javax.management.MBeanServerBuilder} is not specified,
 * the additional behaviour is only added to calls coming in via the remote connector or MBeanServers injected via
 * a dependency on {@link MBeanServerService}.</p>
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PluggableMBeanServerBuilder extends MBeanServerBuilder {

    public PluggableMBeanServerBuilder() {
    }

    @Override
    public MBeanServer newMBeanServer(String defaultDomain, MBeanServer outer, MBeanServerDelegate delegate) {
        return new PluggableMBeanServerImpl(super.newMBeanServer(defaultDomain, outer, delegate), delegate);
    }
}