/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class StandardEvent extends AbstractEvent implements Event {
    private final Map<String, Object> data;

    StandardEvent(final String eventSource, final Map<String, Object> data) {
        super(eventSource);
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    @Override
    public Map<String, Object> getData() {
        return data;
    }
}
