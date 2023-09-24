/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LazyEvent extends AbstractEvent implements Event {

    private final Supplier<Map<String, Object>> data;

    LazyEvent(final String eventSource, final Supplier<Map<String, Object>> data) {
        super(eventSource);
        this.data = data;
    }

    @Override
    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(data.get()));
    }
}
