/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.DelegatingServiceBuilder;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * A {@link ServiceBuilder} decorator whose installed {@link Service} will start and/or stop asynchronously.
 * This both reduces boilerplate code, and ensures that service implementors implement async behavior correctly.
 * @author Paul Ferraro
 * @deprecated Superseded by {@link ServiceInstaller.BlockingBuilder#async(Supplier, Supplier)}.
 */
@Deprecated(forRemoval = true)
public class AsyncServiceBuilder<T> extends DelegatingServiceBuilder<T> {

    /**
     * Enumerates the {@link Service} methods that should execute asynchronously.
     */
    public static enum Async {
        START_AND_STOP(true, true),
        START_ONLY(true, false),
        STOP_ONLY(false, true),
        ;
        private final boolean start;
        private final boolean stop;

        Async(boolean start, boolean stop) {
            this.start = start;
            this.stop = stop;
        }
    }

    private final Supplier<Executor> executor;
    private final Async async;

    /**
     * Decorates a service builder transparently applying asynchronous start/stop semantics.
     * @param builder a service builder
     * @param executor an executor provider
     */
    public AsyncServiceBuilder(ServiceBuilder<T> builder, Supplier<Executor> executor) {
        this(builder, executor, Async.START_AND_STOP);
    }

    /**
     * Decorates a service builder transparently applying asynchronous start/stop semantics.
     * @param builder a service builder
     * @param executor an executor provider
     * @param async indicates the {@link Service} methods that should execute asynchronously
     */
    public AsyncServiceBuilder(ServiceBuilder<T> builder, Supplier<Executor> executor, Async async) {
        super(builder);
        this.executor = executor;
        this.async = async;
    }

    @Override
    public ServiceBuilder<T> setInstance(Service service) {
        return super.setInstance(new AsyncService(service, this.executor, this.async));
    }

    private static class AsyncService implements Service {
        private final Service service;
        private final Supplier<Executor> executor;
        private final Async async;

        AsyncService(Service service, Supplier<Executor> executor, Async async) {
            this.service = service;
            this.executor = executor;
            this.async = async;
        }

        @Override
        public void start(final StartContext context) throws StartException {
            if (this.async.start) {
                Runnable task = () -> {
                    try {
                        this.service.start(context);
                        context.complete();
                    } catch (StartException e) {
                        context.failed(e);
                    } catch (Throwable e) {
                        context.failed(new StartException(e));
                    }
                };
                try {
                    this.executor.get().execute(task);
                } catch (RejectedExecutionException e) {
                    task.run();
                } finally {
                    context.asynchronous();
                }
            } else {
                this.service.start(context);
            }
        }

        @Override
        public void stop(final StopContext context) {
            if (this.async.stop) {
                Runnable task = () -> {
                    try {
                        this.service.stop(context);
                    } finally {
                        context.complete();
                    }
                };
                try {
                    this.executor.get().execute(task);
                } catch (RejectedExecutionException e) {
                    task.run();
                } finally {
                    context.asynchronous();
                }
            } else {
                this.service.stop(context);
            }
        }
    }
}
