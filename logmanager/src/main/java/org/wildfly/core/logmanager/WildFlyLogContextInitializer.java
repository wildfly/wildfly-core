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

import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.LogContextInitializer;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class WildFlyLogContextInitializer implements LogContextInitializer {
    @Override
    public Level getInitialLevel(final String loggerName) {
        // TODO (jrp) we might need a system property for this
        return LogContextInitializer.super.getInitialLevel(loggerName);
    }

    @Override
    public Level getMinimumLevel(final String loggerName) {
        // TODO (jrp) we might need a system property for this
        return LogContextInitializer.super.getMinimumLevel(loggerName);
    }

    @Override
    public Handler[] getInitialHandlers(final String loggerName) {
        // TODO (jrp) here we put the DelayedHandler. Possibly a static one so we have access to it
        return LogContextInitializer.super.getInitialHandlers(loggerName);
    }

    @Override
    public boolean useStrongReferences() {
        return true;
    }
}
