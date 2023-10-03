/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
// TODO (jrp) we can probably replace this with the ConfigurationResource
interface ObjectProducer {

    SimpleObjectProducer NULL_PRODUCER = new SimpleObjectProducer(null);

    Object getObject();
}

class SimpleObjectProducer implements ObjectProducer {

    final Object value;

    SimpleObjectProducer(final Object value) {
        this.value = value;
    }

    public Object getObject() {
        return value;
    }
}

class RefProducer implements ObjectProducer {

    private final String name;
    private final Map<String, ?> refs;

    RefProducer(final String name, final Map<String, ?> refs) {
        this.name = name;
        this.refs = refs;
    }

    public Object getObject() {
        return refs.get(name);
    }
}