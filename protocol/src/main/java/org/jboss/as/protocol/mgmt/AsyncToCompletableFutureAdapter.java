/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.protocol.mgmt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.threads.AsyncFuture;

/**
 * Utility that adapts an {@link AsyncFuture} to a {@link CompletableFuture}
 * by using an {@link AsyncFuture.Listener} to complete or cancel the {@code CompletableFuture}
 * and by ensuring calls to {@link CompletableFuture#cancel(boolean)} result in appropriate
 * cancellation of the {@link AsyncFuture}.
 */
public final class AsyncToCompletableFutureAdapter {

    /**
     * Adapt the given {@code asyncFuture} to complete or cancel a {@link CompletableFuture}.
     *
     * @param asyncFuture the future that will provide the value. Cannot be {@code null}
     * @param <T>  the type provided by the futures
     */
    public static <T> CompletableFuture<T> adapt(AsyncFuture<T> asyncFuture) {
        return adapt(asyncFuture, t -> t, null);
    }

    /**
     * Adapt the given {@code asyncFuture} to complete or cancel a {@link CompletableFuture}.
     * The given {@code transformer} function is applied before
     * {@link CompletableFuture#complete(Object) completing} the {@code CompletableFuture},
     * allowing the {@code CompletableFuture} to provide a different type than
     * the {@code AsyncFuture}.
     *
     * @param asyncFuture the future that will provide the value. Cannot be {@code null}
     * @param transformer a function to convert the type provided by {@code asyncFuture}
     *                    to that provided by the {@link CompletableFuture}. Cannot be {@code null}
     * @param asyncCancelTask optional function to perform async cancellation of the ActiveOperation,
     *                        instead of a call to {@link org.jboss.threads.AsyncFutureTask#asyncCancel(boolean)}.
     *                        May be {@code null}
     * @param <T>  the type provided by the {@code asyncFuture}
     * @param <U>  the type to be provided by the @{code CompletableFuture}
     */
    public static <T, U> CompletableFuture<U> adapt(AsyncFuture<T> asyncFuture, Function<T, U> transformer,
                                                    Consumer<Boolean> asyncCancelTask) {
        return new Adapter<>(asyncFuture, transformer, asyncCancelTask);
    }

    private static class Adapter<T, U> extends CompletableFuture<U> {

        private final AsyncFuture<T> asyncFuture;
        private final Consumer<Boolean> asyncCancelConsumer;
        private final AtomicBoolean cancellable = new AtomicBoolean(false);
        private final CountDownLatch latch = new CountDownLatch(1);

        private Adapter(AsyncFuture<T> asyncFuture, Function<T, U> transformer, Consumer<Boolean> asyncCancelConsumer) {
            this.asyncFuture = asyncFuture;
            this.asyncCancelConsumer = asyncCancelConsumer == null ? asyncFuture::asyncCancel : asyncCancelConsumer;
            asyncFuture.addListener(new AsyncFuture.Listener<T, Object>() {

                @Override
                public void handleComplete(AsyncFuture<? extends T> future, Object attachment) {
                    try {
                        Adapter.this.complete(transformer.apply(future.get()));
                    } catch (InterruptedException | ExecutionException e) {
                        Adapter.this.completeExceptionally(e);
                    } finally {
                        latch.countDown();
                    }
                }

                @Override
                public void handleFailed(AsyncFuture<? extends T> future, Throwable cause, Object attachment) {
                    try {
                        Adapter.this.completeExceptionally(cause);
                    } finally {
                        latch.countDown();
                    }

                }

                @Override
                public void handleCancelled(AsyncFuture<? extends T> future, Object attachment) {
                    try {
                        Adapter.this.internalCancel();
                    } finally {
                        latch.countDown();
                    }
                }
            }, this);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {

            // Only try and cancel our asyncFuture once
            if (!cancellable.compareAndSet(false, true)) {
                // If another thread is trying to cancel the asyncFuture, await their work
                awaitLatch();
                return super.cancel(mayInterruptIfRunning);
            }

            try {
                // Cancel our AsyncFuture.
                // These next two lines mimic the impl of AsyncFutureTask.cancel(boolean), except
                // instead of the first line being a call to AsyncFutureTask.asyncCancel(boolean)
                // we allow calling an alternative function passed into our constructor.
                // Passing in such a function takes the place of some old code that wrapped the
                // AsyncFuture in a delegating subclass that overrode asyncCancel.
                asyncCancelConsumer.accept(mayInterruptIfRunning);
                boolean afCancelled = asyncFuture.awaitUninterruptibly() == AsyncFuture.Status.CANCELLED;

                if (!afCancelled) {
                    // Our AsyncFuture.Listener completes this future asynchronously,
                    // so block until it has done so before providing the resulting
                    // state by calling super.cancel.
                    //
                    // We do this because canceling the async future may result in
                    // the canceled management op returning with either outcome=cancelled
                    // or outcome=failed. We're in this block, so we know it's the latter.
                    // We know this will result in the listener, in another
                    // thread, eventually trying to complete this exceptionally.
                    // We don't want the determination of whether this method returns
                    // true or false to be decided by a race between our call below to
                    // super.cancel and the listener thread completing the
                    // future as failed. So we block to let the listener thread win the race.
                    //
                    // An alternative approach would be to call super.cancel first before
                    // cancelling the asyncFuture. But that could result in inconsistency
                    // between what this future reports vs the 'outcome' value of the
                    // executed op. When ModelControllerClient async methods formerly returned an
                    // AsyncFuture these results were consistent, so we're maintaining
                    // that behavior with the CompletableFuture we now return.
                    awaitLatch();
                }
                return super.cancel(mayInterruptIfRunning);
            } finally {
                // Code hardening:
                // If for some reason asyncFuture.awaitUninterruptibly() returns CANCELLED
                // without the listener getting invoked, now that we're cancelled make sure
                // the latch is counted down so other calls to cancel don't block.
                latch.countDown();
            }
        }

        private void internalCancel() {
            super.cancel(false);
        }

        private void awaitLatch() {
            try {
                latch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
