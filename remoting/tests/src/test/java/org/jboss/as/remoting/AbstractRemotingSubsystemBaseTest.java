/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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
package org.jboss.as.remoting;

import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.jboss.as.remoting.RemotingSubsystemTestUtil.DEFAULT_ADDITIONAL_INITIALIZATION;

/**
 * @author <a href="opalka.richard@gmail.com">Richard Opalka</a>
 */
abstract class AbstractRemotingSubsystemBaseTest extends AbstractSubsystemSchemaTest<RemotingSubsystemSchema> {

    AbstractRemotingSubsystemBaseTest(RemotingSubsystemSchema schema) {
        super(RemotingExtension.SUBSYSTEM_NAME, new RemotingExtension(), schema, RemotingSubsystemSchema.CURRENT);
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return DEFAULT_ADDITIONAL_INITIALIZATION;
    }

    static final class DependenciesRetrievalService implements Service {
        private static final ServiceName DEPS_RETRIEVAL_SERVICE = ServiceName.JBOSS.append("dependencies", "retrieval", "service");
        private final Map<ServiceName, Supplier<Object>> dependencies;
        private boolean down = true;

        private DependenciesRetrievalService(final Map<ServiceName, Supplier<Object>> dependencies) {
            this.dependencies = dependencies;
        }

        static DependenciesRetrievalService create(final KernelServices services, final ServiceName... dependencies) {
            final ServiceBuilder<?> sb = services.getContainer().addService(DEPS_RETRIEVAL_SERVICE);
            final Map<ServiceName, Supplier<Object>> deps = new HashMap<>(dependencies.length);
            for (ServiceName dependency : dependencies) {
                deps.put(dependency, sb.requires(dependency));
            }
            final DependenciesRetrievalService instance = new DependenciesRetrievalService(deps);
            sb.setInstance(instance);
            sb.install();
            return instance;
        }

        @Override
        public synchronized void start(final StartContext startContext) {
            down = false;
            notifyAll();
        }

        @Override
        public synchronized void stop(final StopContext stopContext) {
            down = true;
        }

        @SuppressWarnings("unchecked")
        synchronized <T> T getService(final ServiceName name) {
            while (down) {
                try { wait(); } catch (Throwable ignored) {}
            }
            return (T) dependencies.get(name).get();
        }
    }
}
