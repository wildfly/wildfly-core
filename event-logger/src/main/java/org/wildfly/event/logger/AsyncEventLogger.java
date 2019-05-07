/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.event.logger;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class AsyncEventLogger extends AbstractEventLogger implements EventLogger, Runnable {

    //0 = not running
    //1 = queued
    //2 = running
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<AsyncEventLogger> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(AsyncEventLogger.class, "state");

    private final EventWriter writer;
    private final Executor executor;
    private final Deque<Event> pendingMessages;

    AsyncEventLogger(final String id, final EventWriter writer, final Executor executor) {
        super(id);
        this.writer = writer;
        this.executor = executor;
        pendingMessages = new ConcurrentLinkedDeque<>();
    }

    @Override
    void log(final Event event) {
        pendingMessages.add(event);
        int state = stateUpdater.get(this);
        if (state == 0) {
            if (stateUpdater.compareAndSet(this, 0, 1)) {
                executor.execute(this);
            }
        }
    }

    @Override
    public void run() {
        if (!stateUpdater.compareAndSet(this, 1, 2)) {
            return;
        }
        List<Event> events = new ArrayList<>();
        Event event;
        // Only grab at most 1000 messages at a time
        for (int i = 0; i < 1000; ++i) {
            event = pendingMessages.poll();
            if (event == null) {
                break;
            }
            events.add(event);
        }
        try {
            if (!events.isEmpty()) {
                writeMessage(events);
            }
        } finally {
            stateUpdater.set(this, 0);
            // Check to see if there is still more messages and run again if there are
            if (!events.isEmpty()) {
                if (stateUpdater.compareAndSet(this, 0, 1)) {
                    executor.execute(this);
                }
            }
        }
    }

    private void writeMessage(final List<Event> events) {
        for (Event event : events) {
            writer.write(event);
        }
    }
}
