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
 * @author Emanuel Muckenhuber
 */
abstract class AbstractDelegatingAsyncFuture<T> implements AsyncFuture<T> {

    final AsyncFuture<T> delegate;

    AbstractDelegatingAsyncFuture(AsyncFuture<T> delegate) {
        this.delegate = delegate;
    }


    @Override
    public Status await() throws InterruptedException {
        return delegate.await();
    }

    @Override
    public Status await(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.await(timeout, unit);
    }

    @Override
    public Status awaitUninterruptibly() {
        return delegate.awaitUninterruptibly();
    }

    @Override
    public Status awaitUninterruptibly(long timeout, TimeUnit unit) {
        return delegate.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public Status getStatus() {
        return delegate.getStatus();
    }

    @Override
    public boolean cancel(boolean interruptionDesired) {
        // allow custom cancellation policies
        asyncCancel(interruptionDesired);
        return awaitUninterruptibly() == Status.CANCELLED;
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public T getUninterruptibly() throws CancellationException, ExecutionException {
        return delegate.getUninterruptibly();
    }

    @Override
    public T getUninterruptibly(long timeout, TimeUnit unit) throws CancellationException, ExecutionException, TimeoutException {
        return delegate.getUninterruptibly(timeout, unit);
    }

    public <A> void addListener(Listener<? super T, A> aListener, A attachment) {
        delegate.addListener(aListener, attachment);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }
}
