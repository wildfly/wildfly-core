/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.time.Instant;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractEvent implements Event {
    private final String eventSource;
    private final Instant instant;

    AbstractEvent(final String eventSource) {
        this.eventSource = eventSource;
        instant = Instant.now();
    }

    @Override
    public String getSource() {
        return eventSource;
    }

    @Override
    public Instant getInstant() {
        return instant;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[eventSource=" + eventSource + ", instant=" + instant + ", data=" + getData() + "]";
    }
}
