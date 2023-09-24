/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.util.Map;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractEventLogger implements EventLogger {
    private final String eventSource;

    AbstractEventLogger(final String eventSource) {
        this.eventSource = eventSource;
    }

    @Override
    public EventLogger log(final Map<String, Object> event) {
        log(new StandardEvent(eventSource, event));
        return this;
    }

    @Override
    public EventLogger log(final Supplier<Map<String, Object>> event) {
        log(new LazyEvent(eventSource, event));
        return this;
    }

    @Override
    public String getEventSource() {
        return eventSource;
    }

    /**
     * Handles logging the created event.
     *
     * @param event the event to log
     */
    abstract void log(Event event);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + eventSource + "]";
    }
}
