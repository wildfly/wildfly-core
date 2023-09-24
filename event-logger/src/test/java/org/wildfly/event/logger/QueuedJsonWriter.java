/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class QueuedJsonWriter implements EventWriter {

    private final JsonEventFormatter formatter;

    final BlockingDeque<String> events = new LinkedBlockingDeque<>();

    QueuedJsonWriter() {
        this.formatter = JsonEventFormatter.builder().build();
    }

    @Override
    public void write(final Event event) {
        events.add(formatter.format(event));
    }

    @Override
    public void close() {
        events.clear();
    }
}
