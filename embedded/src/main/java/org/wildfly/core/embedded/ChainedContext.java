/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

import java.util.ArrayList;
import java.util.List;

import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * A {@link Context} that wraps other contexts and invokes them in the order they are {@link #add(Context) added}.
 *
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
