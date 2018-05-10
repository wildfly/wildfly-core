/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.embedded;

import java.util.ArrayList;
import java.util.List;

import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ChainedContext implements Context {

    private final List<Context> contexts;

    ChainedContext() {
        contexts = new ArrayList<>();
    }

    void add(final Context context) {
        contexts.add(context);
    }

    @Override
    public void activate() {
        for (Context context : contexts) {
            context.activate();
        }
    }

    @Override
    public void restore() {
        for (int i = (contexts.size() - 1); i >= 0; i--) {
            final Context context = contexts.get(i);
            try {
                context.restore();
            } catch (Exception e) {
                EmbeddedLogger.ROOT_LOGGER.failedToRestoreContext(e, context);
            }
        }
    }
}
