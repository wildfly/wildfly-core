/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.wildfly.common.Assert;
import org.xnio.Cancellable;

/** Standard ActiveOperation implementation */
class ActiveOperationImpl<T, A> extends AsyncFutureTask<T> implements ActiveOperation<T, A> {

    // All active operations have to use the direct executor for now. At least we need to make sure
    // completion/cancellation/cleanup are executed before further requests are handled.
    private static final Executor directExecutor = Runnable::run;

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
        addListener(new Listener<>() {
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

        this.resultHandler = new ResultHandler<>() {
            @Override
            public boolean done(T result) {
                try {
                    return ActiveOperationImpl.this.setResult(result);
                } finally {
                    handler.removeActiveOperation(operationId);
                }
            }

            /**
             * @param t the exception must not be null
             * @return true if the result was successfully set, or false if a result was already set
             */
            @Override
            public boolean failed(final Throwable t) {
                Assert.checkNotNullParam("Throwable", t);
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
                        ((cancellables == null) ? (this.cancellables = new ArrayList<>()) : cancellables).add(cancellable);
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
