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
