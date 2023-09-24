/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt.domain;

import org.jboss.as.controller.ModelController;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.AsyncFutureTask;
import org.wildfly.common.Assert;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service triggering the registration at the local host-controller and resolving the boot operations.
 * This service depends on the {@code HostControllerConnection} and {@code ModelController}, relying
 * on the fact that the boot() process happens in a different thread and the controller service is already
 * seen as started. The {@code Future} is used in the {@linkplain org.jboss.as.server.Bootstrap.ConfigurationPersisterFactory}
 * to block on the registration result.
 *
 * @author Emanuel Muckenhuber
 */
public class ServerBootOperationsService implements Service<Void> {

    final InjectedValue<ModelController> serverController = new InjectedValue<ModelController>();
    final InjectedValue<HostControllerClient> clientInjector = new InjectedValue<HostControllerClient>();
    final InjectedValue<Executor> executorInjector = new InjectedValue<Executor>();

    private FutureBootUpdates future = new FutureBootUpdates();

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        final HostControllerClient client = clientInjector.getValue();
        final ModelController controller = serverController.getValue();
        final Executor executor = executorInjector.getValue();
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    client.resolveBootUpdates(controller, future);
                    context.complete();
                } catch (Exception e) {
                    future.failed(e);
                    context.failed(new StartException(e));
                }
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        } finally {
            context.asynchronous();
        }
    }

    @Override
    public synchronized void stop(final StopContext context) {
        final FutureBootUpdates updates = this.future;
        this.future = new FutureBootUpdates();
        if(! updates.isDone()) {
            updates.cancelled();
        }
    }

    public Future<ModelNode> getFutureResult() {
        return new Future<ModelNode>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return getFutureTask().cancel(mayInterruptIfRunning);
            }

            @Override
            public boolean isCancelled() {
                return getFutureTask().isCancelled();
            }

            @Override
            public boolean isDone() {
                return getFutureTask().isDone();
            }

            @Override
            public ModelNode get() throws InterruptedException, ExecutionException {
                return getFutureTask().get();
            }

            @Override
            public ModelNode get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return getFutureTask().get(timeout, unit);
            }
        };
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    private synchronized Future<ModelNode> getFutureTask() {
        return future;
    }

    public InjectedValue<HostControllerClient> getClientInjector() {
        return clientInjector;
    }

    public InjectedValue<ModelController> getServerController() {
        return serverController;
    }

    public InjectedValue<Executor> getExecutorInjector() {
        return executorInjector;
    }

    private static class FutureBootUpdates extends AsyncFutureTask<ModelNode> implements ActiveOperation.CompletedCallback<ModelNode> {

        private FutureBootUpdates() {
            super(null);
        }

        @Override
        public void completed(final ModelNode result) {
            setResult(result);
        }

        /**
         * @param e the cause of failure, must not be null
         */
        @Override
        public void failed(final Exception e) {
            Assert.checkNotNullParam("Exception", e);
            super.setFailed(e);
        }

        @Override
        public void cancelled() {
            setCancelled();
        }
    }

}
