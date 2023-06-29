/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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