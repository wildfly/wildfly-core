/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
