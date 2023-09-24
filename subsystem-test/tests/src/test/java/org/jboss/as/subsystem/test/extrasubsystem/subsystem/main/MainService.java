/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.extrasubsystem.subsystem.main;

import org.jboss.as.subsystem.test.extrasubsystem.subsystem.dependency.Dependency;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MainService implements Service<MainService> {

    public static ServiceName NAME = ServiceName.of("test", "service", "main");

    public final InjectedValue<Dependency> dependencyValue = new InjectedValue<Dependency>();

    @Override
    public MainService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

}
