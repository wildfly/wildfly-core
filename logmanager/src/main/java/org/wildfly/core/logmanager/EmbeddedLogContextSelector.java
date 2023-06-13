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

package org.wildfly.core.logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class EmbeddedLogContextSelector extends WildFlyLogContextSelectorImpl {
    private static final LogContext CONTEXT = LogContext.create(true, new WildFlyLogContextInitializer());

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
