/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class EmbeddedLogContextSelector extends WildFlyLogContextSelectorImpl {
    private static final LogContext CONTEXT = LogContext.create(false, new WildFlyLogContextInitializer());

    EmbeddedLogContextSelector() {
        super(CONTEXT);
        clearLogContext();
    }

    // TODO (jrp) this should probably just override the close and replace the old ContextConfiguration if there was one
    private static void clearLogContext() {
        // Remove the configurator and clear the log context
        final ContextConfiguration configuration = CONTEXT.detach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
        if (configuration != null) {
            try {
                configuration.close();
            } catch (Exception e) {
                // TODO (jrp) don't throw this, log something which feels counterintuitive.
                throw new RuntimeException(e);
            }
        }
        try {
            CONTEXT.close();
            // TODO (jrp) don't throw this, log something which feels counterintuitive.
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
