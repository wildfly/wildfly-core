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

package org.jboss.as.server.mgmt;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.jboss.as.domain.http.server.ManagementHttpRequestProcessor;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRequestTracker;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

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
 */
public class HttpShutdownService implements Service<Void> {

    private static final long SHUTDOWN_TIMEOUT = 15;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    private final InjectedValue<Executor> executorValue = new InjectedValue<>();
    private final InjectedValue<ManagementHttpRequestProcessor> processorValue = new InjectedValue<>();
    private final InjectedValue<ManagementChannelRegistryService> mgmtChannelRegistry = new InjectedValue<>();

    private volatile ManagementRequestTracker trackerService;

    @Override
    public synchronized void start(StartContext context) throws StartException {
        // Register the http request processor on the mgmt request tracker
        final ManagementHttpRequestProcessor processor = processorValue.getValue();
        trackerService = mgmtChannelRegistry.getValue().getTrackerService();
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
            executorValue.getValue().execute(new Runnable() {
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

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    public InjectedValue<Executor> getExecutorValue() {
        return executorValue;
    }

    public InjectedValue<ManagementHttpRequestProcessor> getProcessorValue() {
        return processorValue;
    }

    public InjectedValue<ManagementChannelRegistryService> getMgmtChannelRegistry() {
        return mgmtChannelRegistry;
    }
}
