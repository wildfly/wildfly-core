/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.embedded;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.server.ElapsedTime;
import org.jboss.as.server.SystemExiter;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.core.embedded.spi.BootstrappedEmbeddedProcess;
import org.wildfly.core.embedded.spi.EmbeddedModelControllerClientFactory;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrap;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrapConfiguration;
import org.wildfly.core.embedded.spi.ProcessStateNotifier;

/**
 * Base {@link EmbeddedProcessBootstrap} implementation that provides the bootstrap
 * behavior that is common to an embedded {@code StandaloneServer} and {@code HostController}.
 */
public abstract class AbstractEmbeddedProcessBootstrap implements EmbeddedProcessBootstrap {

    @Override
    public BootstrappedEmbeddedProcess startup(EmbeddedProcessBootstrapConfiguration configuration) throws Exception {
        ElapsedTime elapsedTime = ElapsedTime.startingFromNow();

        // Take control of server use of System.exit
        SystemExiter.initialize(new SystemExiter.Exiter() {
            @Override
            public void exit(int status) {
                configuration.getSystemExitCallback().accept(status);
            }
        });

        // As part of bootstrap, install services to capture the ProcessStateNotifier and EmbeddedModelControllerClientFactory.
        // These are started async, so use a latch to await their start.
        CountDownLatch serviceAwait = new CountDownLatch(2);
        AtomicReference<ProcessStateNotifier> notifierRef = new AtomicReference<>();
        ServiceActivator notifierCapture = ctx -> captureValue(ctx, ProcessStateNotifier.SERVICE_NAME,
                new CountdownConsumer<>(serviceAwait, notifierRef));
        AtomicReference<EmbeddedModelControllerClientFactory> clientFactoryRef = new AtomicReference<>();
        ServiceActivator clientFactoryCapture = ctx -> captureValue(ctx, EmbeddedModelControllerClientFactory.SERVICE_NAME,
                new CountdownConsumer<>(serviceAwait, clientFactoryRef));

        Future<ServiceContainer> future = bootstrapEmbeddedProcess(elapsedTime, configuration, notifierCapture, clientFactoryCapture);
        if (future == null) {
            // TODO why do we ignore this?
            return null;
        }

        final ServiceContainer serviceContainer = future.get();
        try {
            if (!serviceAwait.await(30, TimeUnit.SECONDS)) {
                StringBuilder sb = new StringBuilder();
                ProcessStateNotifier psn = notifierRef.get();
                if (psn == null) {
                    sb.append(ProcessStateNotifier.class.getSimpleName());
                }
                if (clientFactoryRef.get() == null) {
                    if (psn == null) {
                        sb.append(" ");
                    }
                    sb.append(EmbeddedModelControllerClientFactory.class.getSimpleName());
                }
                ServerLogger.ROOT_LOGGER.embeddedProcessServicesUnavailable(30, sb.toString());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }

        final ProcessStateNotifier psn = notifierRef.get();
        return new BootstrappedEmbeddedProcess() {
            @Override
            public ServiceContainer getServiceContainer() {
                return serviceContainer;
            }

            @Override
            public ProcessStateNotifier getProcessStateNotifier() {
                return psn;
            }

            @Override
            public EmbeddedModelControllerClientFactory getModelControllerClientFactory() {
                return clientFactoryRef.get();
            }

            @Override
            public void close() {
                SystemExiter.initialize(SystemExiter.Exiter.DEFAULT);
            }
        };
    }

    /**
     * Subclass-specific bootstrap logic. Implementations should use the provided inputs to perform their bootstrap.
     * @param elapsedTime tracker for elapsed time since embedded process 'start'. Cannot be {@code null}.
     * @param configuration configuration information for starting. Cannot be {@code null}.
     * @param extraServices activators for other services to start
     * @return future from which the MSC service container can be obtained
     * @throws Exception if one occurs
     */
    protected abstract Future<ServiceContainer> bootstrapEmbeddedProcess(ElapsedTime elapsedTime,
                                                               EmbeddedProcessBootstrapConfiguration configuration,
                                                               ServiceActivator... extraServices)
            throws Exception;


    private static <T> void captureValue(ServiceActivatorContext ctx, ServiceName toCapture, Consumer<T> captor) {
        ServiceBuilder<?> sb = ctx.getServiceTarget().addService();
        final Supplier<T> result = sb.requires(toCapture);
        sb.setInstance(new Service() {
            @Override
            public void start(StartContext context) {
                captor.accept(result.get());
                context.getController().setMode(ServiceController.Mode.REMOVE);
            }

            @Override
            public void stop(StopContext context) {
            }
        });
        sb.install();
    }

    private static class CountdownConsumer<T> implements Consumer<T> {

        private final CountDownLatch latch;
        private final AtomicReference<T> reference;

        private CountdownConsumer(CountDownLatch latch, AtomicReference<T> reference) {
            this.latch = latch;
            this.reference = reference;
        }

        @Override
        public void accept(T t) {
            reference.set(t);
            latch.countDown();
        }
    }
}