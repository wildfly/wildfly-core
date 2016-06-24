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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.Channel;
import org.jboss.threads.AsyncFuture;
import org.jboss.threads.AsyncFutureTask;
import org.xnio.Cancellable;

/** Standard ActiveOperation implementation */
class ActiveOperationImpl<T, A> extends AsyncFutureTask<T> implements ActiveOperation<T, A> {

    // All active operations have to use the direct executor for now. At least we need to make sure
    // completion/cancellation/cleanup are executed before further requests are handled.
    private static final Executor directExecutor = new Executor() {

        @Override
        public void execute(final Runnable command) {
            command.run();
        }
    };

    private static final List<Cancellable> CANCEL_REQUESTED = Collections.emptyList();

    private final A attachment;
    private final Integer operationId;
    private final ResultHandler<T> resultHandler;
    private List<Cancellable> cancellables;
    private volatile Channel channel;

    ActiveOperationImpl(final Integer operationId, final A attachment, final CompletedCallback<T> callback,
                        final AbstractMessageHandler handler) {
        super(directExecutor);
        this.operationId = operationId;
        this.attachment = attachment;
        addListener(new Listener<T, Object>() {
            @Override
            public void handleComplete(AsyncFuture<? extends T> asyncFuture, Object attachment) {
                try {
                    callback.completed(asyncFuture.get());
                } catch (Exception e) {
                    //
                }

            }

            @Override
            public void handleFailed(AsyncFuture<? extends T> asyncFuture, Throwable cause, Object attachment) {
                if(cause instanceof Exception) {
                    callback.failed((Exception) cause);
                } else {
                    callback.failed(new RuntimeException(cause));
                }
            }

            @Override
            public void handleCancelled(AsyncFuture<? extends T> asyncFuture, Object attachment) {
                handler.removeActiveOperation(operationId);
                callback.cancelled();
                ProtocolLogger.ROOT_LOGGER.debugf("cancelled operation (%d) attachment: (%s) handler: %s.", getOperationId(), getAttachment(), handler);
            }
        }, null);

        this.resultHandler = new ResultHandler<T>() {
            @Override
            public boolean done(T result) {
                try {
                    return ActiveOperationImpl.this.setResult(result);
                } finally {
                    handler.removeActiveOperation(operationId);
                }
            }

            @Override
            public boolean failed(Throwable t) {
                try {
                    boolean failed = ActiveOperationImpl.this.setFailed(t);
                    if(failed) {
                        ProtocolLogger.ROOT_LOGGER.debugf(t, "active-op (%d) failed %s", operationId, attachment);
                    }
                    return failed;
                } finally {
                    handler.removeActiveOperation(operationId);
                }
            }

            @Override
            public void cancel() {
                ProtocolLogger.CONNECTION_LOGGER.debugf("Operation (%d) cancelled", operationId);
                ActiveOperationImpl.this.cancel();
            }
        };
    }

    @Override
    public Integer getOperationId() {
        return operationId;
    }

    @Override
    public ResultHandler<T> getResultHandler() {
        return resultHandler;
    }

    @Override
    public A getAttachment() {
        return attachment;
    }

    @Override
    public AsyncFuture<T> getResult() {
        return this;
    }

    @Override
    public void asyncCancel(boolean interruptionDesired) {
        final List<Cancellable> cancellables;
        synchronized (this) {
            cancellables = this.cancellables;
            if (cancellables == CANCEL_REQUESTED) {
                return;
            }
            this.cancellables = CANCEL_REQUESTED;
            if(cancellables == null) {
                setCancelled();
                return;
            }
        }
        for (Cancellable cancellable : cancellables) {
            cancellable.cancel();
        }
        setCancelled();
    }

    @Override
    public void addCancellable(final Cancellable cancellable) {
        // Perhaps just use the IOFuture from XNIO...
        synchronized (this) {
            switch (getStatus()) {
                case CANCELLED:
                    break;
                case WAITING:
                    final List<Cancellable> cancellables = this.cancellables;
                    if (cancellables == CANCEL_REQUESTED) {
                        break;
                    } else {
                        ((cancellables == null) ? (this.cancellables = new ArrayList<Cancellable>()) : cancellables).add(cancellable);
                    }
                default:
                    return;
            }
        }
        cancellable.cancel();
    }

    public boolean cancel() {
        return super.cancel(true);
    }

    Channel getChannel() {
        return channel;
    }

    void updateChannelRef(Channel channel) {
        if (this.channel == null) {
            this.channel = channel;
        }
    }

}
