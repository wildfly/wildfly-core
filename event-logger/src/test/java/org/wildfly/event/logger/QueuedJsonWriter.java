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
