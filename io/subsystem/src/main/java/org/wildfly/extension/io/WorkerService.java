/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2013, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.wildfly.extension.io;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.net.CidrAddressTable;
import org.wildfly.extension.io.logging.IOLogger;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class WorkerService implements Service<XnioWorker> {

    private final XnioWorker.Builder builder;
    private final Consumer<XnioWorker> workerConsumer;
    private final Supplier<ExecutorService> executorSupplier;
    private final Object stopLock = new Object();
    private XnioWorker worker;
    private volatile StopContext stopContext;

    /**
     * @deprecated
     */
    @Deprecated
    public WorkerService(OptionMap optionMap) {
        this(Xnio.getInstance().createWorkerBuilder().populateFromOptions(optionMap));
    }

    /**
     * @deprecated
     */
    @Deprecated
    public WorkerService(final XnioWorker.Builder builder) {
        this(null, () -> Executors.newFixedThreadPool(1), builder);
    }

    public WorkerService(final Consumer<XnioWorker> workerConsumer, final Supplier<ExecutorService> executorSupplier, final XnioWorker.Builder builder) {
        this.workerConsumer = workerConsumer;
        this.executorSupplier = executorSupplier;
        this.builder = builder;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        builder.setTerminationTask(this::stopDone);
        worker = builder.build();
        // TODO: when deprecated constructors usages are eliminated eliminate this null check
        if (workerConsumer != null) {
            workerConsumer.accept(worker);
        }
    }

    @Override
    public void stop(final StopContext context) {
        this.stopContext = context;
        final ExecutorService executorService = executorSupplier.get();
        Runnable asyncStop = () -> {
            XnioWorker localWorker = worker;
            // TODO: when deprecated constructors usages are eliminated eliminate this null check
            if (workerConsumer != null) {
                workerConsumer.accept(null);
            }
            worker = null;
            localWorker.shutdown();
            boolean interrupted = false;
            try {
                synchronized (stopLock) {
                    if (stopContext != null) {
                        // stopDone has not been called by the worker yet
                        try {
                            // Hack. Give in progress tasks a chance to complete before we interrupt.
                            // If we are shutting down gracefully this is redundant as the
                            // graceful shutdown timeout gives tasks a chance, but if we aren't
                            // graceful this helps a bit
                            stopLock.wait(100);
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                }
                if (stopContext != null) {
                    // Tasks are still running. Interrupt those and submit
                    // any unstarted ones to the management executor
                    List<Runnable> tasks = localWorker.shutdownNow();
                    for (Runnable task : tasks) {
                        IOLogger.ROOT_LOGGER.debugf("Worker was shut down forcibly. Submitting task %s to the management executor", task);
                        executorService.submit(task);
                    }
                }
            } finally {
                // TODO xnio doesn't seem to invoke its terminateTask
                // following a shutdownNow() so we'll do it to ensure
                // context.complete() is called
                stopDone();

                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        try {
            try {
                executorService.execute(asyncStop);
            } catch (RejectedExecutionException e) {
                asyncStop.run();
            }
        } finally {
            context.asynchronous();
        }
    }

    // Callback from the worker when it terminates
    private void stopDone() {
        synchronized (stopLock) {
            final StopContext stopContext = this.stopContext;
            this.stopContext = null;
            if (stopContext != null) {
                stopContext.complete();
            }
            stopLock.notifyAll();
        }
    }

    CidrAddressTable<InetSocketAddress> getBindingsTable() {
        return builder.getBindAddressConfigurations();
    }

    @Override
    public XnioWorker getValue() throws IllegalStateException, IllegalArgumentException {
        return worker;
    }
}
