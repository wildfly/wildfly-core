/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

/**
 * A writer used to write events.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface EventWriter extends AutoCloseable {

    /**
     * Writes the event.
     *
     * @param event the event to write
     */
    void write(Event event);
}
