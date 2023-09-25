/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.time.Instant;
import java.util.Map;

/**
 * Describes an event that has taken place.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface Event {

    /**
     * The source of this event.
     *
     * @return the source of this event
     */
    String getSource();

    /**
     * The date this event was created.
     *
     * @return the date the event was created
     */
    @SuppressWarnings("unused")
    Instant getInstant();

    /**
     * The data associated with this event.
     *
     * @return the data for this event
     */
    Map<String, Object> getData();
}
