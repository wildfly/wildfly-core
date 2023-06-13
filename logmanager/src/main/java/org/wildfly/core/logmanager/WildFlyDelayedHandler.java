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

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.stream.Stream;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.logmanager.handlers.DelayedHandler;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class WildFlyDelayedHandler extends DelayedHandler {
    private final Thread shutdownHandler;

    public WildFlyDelayedHandler(final LogContext logContext) {
        super(logContext);
        shutdownHandler = new Thread(() -> {
            Formatter formatter = getFormatter();
            if (formatter == null) {
                formatter = new PatternFormatter("%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n");
            }
            final ConsoleHandler consoleHandler = new ConsoleHandler(formatter);
            super.addHandler(consoleHandler);
        });
        Runtime.getRuntime().addShutdownHook(shutdownHandler);
    }

    @Override
    public void addHandler(final Handler handler) throws SecurityException {
        Runtime.getRuntime().removeShutdownHook(shutdownHandler);
        super.addHandler(handler);
    }

    @Override
    public Handler[] setHandlers(final Handler[] newHandlers) throws SecurityException {
        Runtime.getRuntime().removeShutdownHook(shutdownHandler);
        return super.setHandlers(newHandlers);
    }

    // TODO (jrp) this should probably live elsewhere
    public static void reset() throws SecurityException {
        final var configuration = LogContextConfiguration.getInstance();
        // Find this DelayedHandler on the logger if it exists
        final var handlers = configuration.getLogger("").getHandlers();
        if (handlers != null && handlers.length > 0) {
            // Find the DelayedHandler
            final var delayedHandler = Stream.of(handlers)
                    .filter(handler -> handler.getClass().getName().equals(WildFlyDelayedHandler.class.getName()))
                    .findFirst();
            if (delayedHandler.isPresent()) {
                final WildFlyDelayedHandler handler = (WildFlyDelayedHandler) delayedHandler.get();
                handler.lock.lock();
                try {
                    final Handler[] childHandlers = handler.clearHandlers();
                    // Safely close each child handler
                    for (var h : childHandlers) {
                        try {
                            h.close();
                        } catch (Exception ignore) {
                            // TODO (jrp) should we actually log something here?
                        }
                    }
                    WildFlyConfiguratorFactory.reset();
                } finally {
                    handler.lock.unlock();
                }
            }
        }
    }

    public static void setHandlers(final Logger logger, final Handler[] newHandlers) throws SecurityException {
        // Find this DelayedHandler on the logger if it exists
        final var handlers = logger.getHandlers();
        if (handlers == null || handlers.length == 0) {
            // Set the handlers on the logger
            logger.setHandlers(newHandlers);
        } else {
            // Find the DelayedHandler
            final var delayedHandler = Stream.of(handlers)
                    .filter(handler -> handler.getClass().getName().equals(WildFlyDelayedHandler.class.getName()))
                    .findFirst();
            if (delayedHandler.isPresent()) {
                ((ExtHandler) delayedHandler.get()).setHandlers(newHandlers);
            } else {
                logger.setHandlers(newHandlers);
            }
        }
    }
}
