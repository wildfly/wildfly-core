/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.jmx;

import java.util.Comparator;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.server.Services;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.threads.AsyncFuture;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class JMXTestBase extends AbstractSubsystemTest {
    protected JMXTestBase(String mainSubsystemName, Extension mainExtension) {
        super(mainSubsystemName, mainExtension);
    }

    protected JMXTestBase(String mainSubsystemName, Extension mainExtension, Comparator<PathAddress> removeOrderComparator) {
        super(mainSubsystemName, mainExtension, removeOrderComparator);
    }

    protected static class JMXAdditionalInitialization extends AdditionalInitialization {
        @Override
        protected void addExtraServices(ServiceTarget target, ServiceContainer container) {
            final TestFutureServiceContainer fsc = new TestFutureServiceContainer(container);
            ServiceBuilder<AsyncFuture<ServiceContainer>> containerBuilder = target.addService(Services.JBOSS_AS, new Service<AsyncFuture<ServiceContainer>>() {
                public void start(StartContext context) throws StartException {
                }

                public void stop(StopContext context) {

                }

                public AsyncFuture<ServiceContainer> getValue() throws IllegalStateException, IllegalArgumentException {
                    return fsc;
                }
            });
            containerBuilder.install();
        }
    }
}
