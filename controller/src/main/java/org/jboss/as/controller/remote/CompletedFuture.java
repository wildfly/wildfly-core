/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.remote;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.threads.AsyncFuture;

/**
 * An AsyncFuture that is in {@link org.jboss.threads.AsyncFuture.Status#COMPLETE} as soon as it is instantiated.
 *
 * @author Brian Stansberry
 */
public final class CompletedFuture<T> implements AsyncFuture<T> {

    private final T result;

    /**
     * Creates a future that will provide the given value
     * @param result the value
     */
    public CompletedFuture(T result) {
        this.result = result;
    }

    @Override
    public Status await() throws InterruptedException {
        return Status.COMPLETE;
    }

    @Override
    public Status await(long timeout, TimeUnit unit) throws InterruptedException {
        return Status.COMPLETE;
    }

    @Override
    public T getUninterruptibly() throws CancellationException, ExecutionException {
        return result;
    }

    @Override
    public T getUninterruptibly(long timeout, TimeUnit unit) throws CancellationException, ExecutionException, TimeoutException {
        return result;
    }

    @Override
    public Status awaitUninterruptibly() {
        return Status.COMPLETE;
    }

    @Override
    public Status awaitUninterruptibly(long timeout, TimeUnit unit) {
        return Status.COMPLETE;
    }

    @Override
    public Status getStatus() {
        return Status.COMPLETE;
    }

    @Override
    public <A> void addListener(Listener<? super T, A> listener, A attachment) {
        if (listener != null) {
            listener.handleComplete(this, attachment);
        }
    }

    @Override
    public boolean cancel(boolean interruptionDesired) {
        return false;
    }

    @Override
    public void asyncCancel(boolean interruptionDesired) {
        //
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return result;
    }
}
