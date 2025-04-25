/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static java.security.AccessController.doPrivileged;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.jboss.as.server.Services.requireServerExecutor;
import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.Capabilities.SCHEDULED_EXECUTOR_RUNTIME_CAPABILITY;

import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.threads.JBossThreadFactory;

/**
 * A {@code Service} providing a {@code ScheduledExecutorService} for use across
 * the Elytron subsystem.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ElytronScheduledExecutorService implements Service {

    private static final ServiceName SERVICE_NAME = SCHEDULED_EXECUTOR_RUNTIME_CAPABILITY.getCapabilityServiceName();

    private final Supplier<ExecutorService> shutDownExecutor;
    private final Consumer<ScheduledExecutorService> valueConsumer;

    private volatile ScheduledExecutorService scheduledExecutorService;

    ElytronScheduledExecutorService(Supplier<ExecutorService> shutDownExecutor,
                                    Consumer<ScheduledExecutorService> valueConsumer) {
        this.shutDownExecutor = checkNotNullParam("shutDownExecutor", shutDownExecutor);
        this.valueConsumer = checkNotNullParam("valueConsumer", valueConsumer);
    }

    @Override
    public void start(StartContext context) throws StartException {
        final String namePattern = "ElytronExtension Thread Pool -- %t";
        final ThreadGroup threadGroup = new ThreadGroup("ElytronExtension ThreadGroup");
        final ThreadFactory threadFactory =
                doPrivileged((PrivilegedAction<ThreadFactory>) () ->
                        new JBossThreadFactory(threadGroup, Boolean.FALSE, null, namePattern,
                                null, null));

        // We may make configurable and expand later.
        scheduledExecutorService = newSingleThreadScheduledExecutor(threadFactory);
        valueConsumer.accept(scheduledExecutorService);
    }

    @Override
    public void stop(StopContext context) {
        Runnable r = () -> {
            try {
                scheduledExecutorService.shutdown();
            } finally {
                scheduledExecutorService = null;
                context.complete();
            }
        };

        try {
            shutDownExecutor.get().execute(r);
        } catch (RejectedExecutionException e) {
            r.run();
        } finally {
            context.asynchronous();
        }
    }

    static void installScheduledExecutorService(ServiceTarget serviceTarget) {
        ServiceBuilder<?> serviceBuilder = serviceTarget.addService()
                .setInitialMode(ServiceController.Mode.ON_DEMAND);

        Supplier<ExecutorService> shutDownExecutor = requireServerExecutor(serviceBuilder);
        Consumer<ScheduledExecutorService> valueConsumer = serviceBuilder.provides(SERVICE_NAME);

        serviceBuilder.setInstance(new ElytronScheduledExecutorService(shutDownExecutor, valueConsumer))
                .install();
    }

    static void uninstallScheduledExecutorService(OperationContext context) {
        context.removeService(SERVICE_NAME);
    }
}
