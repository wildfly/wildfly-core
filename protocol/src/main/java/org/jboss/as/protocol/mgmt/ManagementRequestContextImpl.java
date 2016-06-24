/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.protocol.mgmt;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageOutputStream;
import org.xnio.Cancellable;

/** Standard {@code ManagementRequestContext} implementation. */
class ManagementRequestContextImpl<T, A> implements ManagementRequestContext<A> {

    private final ActiveOperation<T, A> support;
    private final Channel channel;
    private final ManagementProtocolHeader header;
    private final Executor executor;

    ManagementRequestContextImpl(ActiveOperation<T, A> support, Channel channel, ManagementProtocolHeader header, Executor executor) {
        this.support = support;
        this.channel = channel;
        this.header = header;
        this.executor = executor;
    }

    @Override
    public Integer getOperationId() {
        return support.getOperationId();
    }

    @Override
    public A getAttachment() {
        return support.getAttachment();
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public ManagementProtocolHeader getRequestHeader() {
        return header;
    }

    Runnable createAsyncTaskRunner(final AsyncTask<A> task, final boolean cancellable) {
        final ManagementRequestContext<A> context = this;
        final AsyncTaskRunner runner = new AsyncTaskRunner(cancellable) {
            @Override
            protected void doExecute() {
                try {
                    task.execute(context);
                } catch (Throwable t) {
                    if(support.getResultHandler().failed(t)) {
                        ManagementProtocolHeader requestHeader;
                        if (task instanceof MultipleResponseAsyncTask) {
                            requestHeader = ((MultipleResponseAsyncTask) task).getCurrentRequestHeader();
                            requestHeader = requestHeader == null ? header : requestHeader;
                        } else {
                            requestHeader = header;
                        }
                        AbstractMessageHandler.safeWriteErrorResponse(channel, requestHeader, t);
                    }
                    ProtocolLogger.ROOT_LOGGER.debugf(t, " failed to process async request for %s on channel %s", task, channel);
                }
            }
        };
        if (cancellable) {
            support.addCancellable(runner);
        }
        return runner;
    }

    @Override
    public boolean executeAsync(final AsyncTask<A> task) {
        return executeAsync(task, true, executor);
    }

    @Override
    public boolean executeAsync(final AsyncTask<A> task, boolean cancellable) {
        return executeAsync(task, cancellable, executor);
    }

    @Override
    public boolean executeAsync(AsyncTask<A> task, Executor executor) {
        return executeAsync(task, true, executor);
    }

    @Override
    public boolean executeAsync(final AsyncTask<A> task, boolean cancellable, final Executor executor) {
        try {
            executor.execute(createAsyncTaskRunner(task, cancellable));
            return true;
        } catch (RejectedExecutionException e) {
            if(support.getResultHandler().failed(e)) {
                AbstractMessageHandler.safeWriteErrorResponse(channel, header, e);
            }
        }
        return false;
    }

    @Override
    public FlushableDataOutput writeMessage(final ManagementProtocolHeader header) throws IOException {
        final MessageOutputStream os = channel.writeMessage();
        return AbstractMessageHandler.writeHeader(header, os);
    }

    private abstract static class AsyncTaskRunner implements Runnable, Cancellable {

        private final boolean cancellable;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Thread thread;

        private AsyncTaskRunner(boolean cancellable) {
            this.cancellable = cancellable;
        }
        @Override
        public Cancellable cancel() {
            if (cancellable && cancelled.compareAndSet(false, true)) {
                final Thread thread = this.thread;
                if(thread != null) {
                    thread.interrupt();
                    ProtocolLogger.ROOT_LOGGER.cancelledAsyncTask(getClass().getSimpleName(), thread);
                }
            }
            return this;
        }

        /**
         * Execute...
         */
        protected abstract void doExecute();

        @Override
        public void run() {
            if (cancellable && cancelled.get()) {
                Thread.currentThread().interrupt();
                ProtocolLogger.ROOT_LOGGER.cancelledAsyncTaskBeforeRun(getClass().getSimpleName());
            }
            this.thread = Thread.currentThread();
            try {
                doExecute();
            } finally {
                this.thread = null;
            }
        }

        final boolean isCancelled() {
            return cancelled.get();
        }
    }
}
