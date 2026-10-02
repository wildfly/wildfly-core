/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An asynchronous {@link EventLogger} that dispatches events to a background thread via an
 * {@link Executor}.
 *
 * <p>The internal queue is bounded to {@value #DEFAULT_QUEUE_CAPACITY} entries by default.
 * Events submitted when the queue is full are dropped and counted; callers can read the total via
 * {@link #getDroppedCount()}.
 *
 * <p>{@link #close()} drains the queue completely before returning, so no events are lost
 * during an orderly shutdown.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class AsyncEventLogger extends AbstractEventLogger implements EventLogger {

    /** Default maximum number of events that may be queued before new arrivals are dropped. */
    static final int DEFAULT_QUEUE_CAPACITY = 1024;

    //0 = not running
    //1 = queued
    //2 = running
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<AsyncEventLogger> stateUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AsyncEventLogger.class, "state");

    private final EventWriter writer;
    private final Executor executor;
    private final BlockingQueue<Event> pendingMessages;
    private final AtomicLong droppedCount = new AtomicLong();
    private volatile boolean closed = false;

    AsyncEventLogger(final String id, final EventWriter writer, final Executor executor) {
        this(id, writer, executor, DEFAULT_QUEUE_CAPACITY);
    }

    AsyncEventLogger(final String id, final EventWriter writer, final Executor executor, final int queueCapacity) {
        super(id);
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be at least 1: " + queueCapacity);
        }
        this.writer = writer;
        this.executor = executor;
        this.pendingMessages = new ArrayBlockingQueue<>(queueCapacity);
    }

    @Override
    void log(final Event event) {
        if (closed) {
            droppedCount.incrementAndGet();
            return;
        }
        if (!pendingMessages.offer(event)) {
            droppedCount.incrementAndGet();
            return;
        }
        int state = stateUpdater.get(this);
        if (state == 0) {
            if (stateUpdater.compareAndSet(this, 0, 1)) {
                executor.execute(this::run);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        // Drain whatever remains in the queue on the calling thread.
        final List<Event> remaining = new ArrayList<>();
        pendingMessages.drainTo(remaining);
        if (!remaining.isEmpty()) {
            writeMessages(remaining);
        }
    }

    @Override
    public long getDroppedCount() {
        return droppedCount.get();
    }

    void run() {
        if (!stateUpdater.compareAndSet(this, 1, 2)) {
            return;
        }
        List<Event> events = new ArrayList<>();
        // Only grab at most 1000 messages at a time
        for (int i = 0; i < 1000; ++i) {
            Event event = pendingMessages.poll();
            if (event == null) {
                break;
            }
            events.add(event);
        }
        try {
            if (!events.isEmpty()) {
                writeMessages(events);
            }
        } finally {
            stateUpdater.set(this, 0);
            // Check to see if there are still more messages and run again if there are
            if (!pendingMessages.isEmpty()) {
                if (stateUpdater.compareAndSet(this, 0, 1)) {
                    executor.execute(this::run);
                }
            }
        }
    }

    private void writeMessages(final List<Event> events) {
        for (Event event : events) {
            writer.write(event);
        }
    }
}
