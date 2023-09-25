/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.impl;

import java.util.concurrent.TimeUnit;

import org.jboss.threads.AsyncFuture;

/**
 * Base class for {@link org.jboss.as.controller.client.impl.AbstractDelegatingAsyncFuture}
 * and {@link org.jboss.as.controller.client.impl.ConvertingDelegatingAsyncFuture} that handles the
 * simple delegation stuff.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
abstract class BasicDelegatingAsyncFuture<T, D> implements AsyncFuture<T> {

    final AsyncFuture<D> delegate;

    BasicDelegatingAsyncFuture(AsyncFuture<D> delegate) {
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
}
