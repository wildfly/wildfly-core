/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class StandardEventLogger extends AbstractEventLogger implements EventLogger {

    private final EventWriter writer;

    StandardEventLogger(final String eventSource, final EventWriter writer) {
        super(eventSource);
        this.writer = writer;
    }

    @Override
    void log(final Event event) {
        writer.write(event);
    }

}
