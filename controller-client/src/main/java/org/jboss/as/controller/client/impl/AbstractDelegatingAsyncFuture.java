/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.impl;

import org.jboss.threads.AsyncFuture;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractDelegatingAsyncFuture<T> extends BasicDelegatingAsyncFuture<T, T> {

    public AbstractDelegatingAsyncFuture(AsyncFuture<T> delegate) {
        super(delegate);
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
