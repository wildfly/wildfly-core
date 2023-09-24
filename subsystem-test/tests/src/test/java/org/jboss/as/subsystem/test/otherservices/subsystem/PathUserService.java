/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.otherservices.subsystem;

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
public class PathUserService implements Service<PathUserService> {

    public static final ServiceName NAME = ServiceName.of("test", "binding", "user");
    public final InjectedValue<String> pathValue = new InjectedValue<String>();

    @Override
    public PathUserService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext arg0) throws StartException {
    }

    @Override
    public void stop(StopContext arg0) {
    }
}
