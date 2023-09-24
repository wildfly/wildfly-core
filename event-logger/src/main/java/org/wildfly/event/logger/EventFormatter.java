/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

/**
 * A formatter for formatting events.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface EventFormatter {

    /**
     * Formats the event into a string.
     *
     * @param event the event to format
     *
     * @return the formatted string
     */
    String format(Event event);
}
