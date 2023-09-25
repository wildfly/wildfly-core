/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.as.domain.http.server.ManagementHttpRequestProcessor;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRequestTracker;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Service preventing the http service from shutting down and closing the channels before
 * the operation was able to complete. This is mainly important to be able to write the prepared response
 * for lifecycle operations (:reload, :shutdown).
 *
 * In general this service needs to set up a service dependency on the management http server, so that it cannot shutdown
 * until this service {@code #stop()} method completes.
 *
 * Beside active http requests this also waits for all other active management requests, since in case http-upgrade
 * was used mgmt operations are now tracked using the {@linkplain ManagementChannelOpenListenerService}.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class HttpShutdownService implements Service {

    private static final long SHUTDOWN_TIMEOUT = 15;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    private final Supplier<Executor> executorSupplier;
    private final Supplier<ManagementHttpRequestProcessor> processorSupplier;
    private final Supplier<ManagementChannelRegistryService> registrySupplier;

    private volatile ManagementRequestTracker trackerService;

    public HttpShutdownService(final Supplier<Executor> executorSupplier,
                               final Supplier<ManagementHttpRequestProcessor> processorSupplier,
                               final Supplier<ManagementChannelRegistryService> registrySupplier) {
        this.executorSupplier = executorSupplier;
        this.processorSupplier = processorSupplier;
        this.registrySupplier = registrySupplier;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        // Register the http request processor on the mgmt request tracker
        final ManagementHttpRequestProcessor processor = processorSupplier.get();
        trackerService = registrySupplier.get().getTrackerService();
        trackerService.registerTracker(processor);
        processor.addShutdownListener(new ManagementHttpRequestProcessor.ShutdownListener() {
            @Override
            public void handleCompleted() {
                trackerService.unregisterTracker(processor);
            }
        });
    }

    @Override
    public synchronized void stop(final StopContext context) {
        // Signal all management services we are about to shutdown.
        trackerService.prepareShutdown();
        context.asynchronous();
        try {
            executorSupplier.get().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Wait for all mgmt requests to complete
                        trackerService.awaitShutdown(SHUTDOWN_TIMEOUT, TIME_UNIT);
                    } catch (InterruptedException e) {
                        //
                    } finally {
                        context.complete();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            context.complete();
        }
    }
}
