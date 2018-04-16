/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.logging;

import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.FATAL;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.TRACE;
import static org.jboss.logging.Logger.Level.WARN;

import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.logging.NDC;
import org.wildfly.test.undertow.UndertowServiceActivator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingServiceActivator extends UndertowServiceActivator {
    public static final String DEFAULT_MESSAGE = "Default log message";
    public static final Logger.Level[] LOG_LEVELS = {DEBUG, TRACE, INFO, WARN, ERROR, FATAL};
    public static final String MSG_KEY = "msg";
    public static final String INCLUDE_LEVEL_KEY = "includeLevel";
    public static final String LOG_INFO_ONLY_KEY = "logInfoOnly";
    public static final String NDC_KEY = "ndc";
    public static final String LOG_EXCEPTION_KEY = "logException";
    public static final Logger LOGGER = Logger.getLogger(LoggingServiceActivator.class);

    @Override
    protected HttpHandler getHttpHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final Map<String, Deque<String>> params = new TreeMap<>(exchange.getQueryParameters());
                final String msg = getValue(params, MSG_KEY, DEFAULT_MESSAGE);
                final boolean includeLevel = getValue(params, INCLUDE_LEVEL_KEY, false);
                final boolean logInfoOnly = getValue(params, LOG_INFO_ONLY_KEY, false);
                final boolean logException = getValue(params, LOG_EXCEPTION_KEY, false);
                final String ndcValue = getValue(params, NDC_KEY, null);
                if (ndcValue != null) {
                    NDC.push(ndcValue);
                }
                // Assume other parameters are MDC key/value pairs
                for (String key : params.keySet()) {
                    MDC.put(key, params.get(key).getFirst());
                }
                if (logInfoOnly) {
                    LOGGER.info(msg);
                } else {
                    for (Logger.Level level : LOG_LEVELS) {
                        if (includeLevel) {
                            if (logException) {
                                LOGGER.log(level, formatMessage(msg, level), createMultiNestedCause());
                            } else {
                                LOGGER.log(level, formatMessage(msg, level));
                            }
                        } else {
                            if (logException) {
                                LOGGER.log(level, msg, createMultiNestedCause());
                            } else {
                                LOGGER.log(level, msg);
                            }
                        }
                    }
                }
                // Clear NDC and MDC
                NDC.clear();
                MDC.clear();
                exchange.getResponseSender().send("Response sent");
            }
        };
    }

    private static String getValue(final Map<String, Deque<String>> params, final String key, final String dft) {
        final Deque<String> values = params.remove(key);
        if (values != null) {
            return values.getFirst();
        }
        return dft;
    }

    private static boolean getValue(final Map<String, Deque<String>> params, final String key, final boolean dft) {
        final Deque<String> values = params.remove(key);
        if (values != null) {
            return Boolean.parseBoolean(values.getFirst());
        }
        return dft;
    }

    private static Throwable createMultiNestedCause() {
        final RuntimeException suppressed1 = new RuntimeException("Suppressed 1");
        final IllegalStateException nested1 = new IllegalStateException("Nested 1");
        nested1.addSuppressed(new RuntimeException("Nested 1a"));
        suppressed1.addSuppressed(nested1);
        suppressed1.addSuppressed(new IllegalStateException("Nested 1-2"));

        final RuntimeException suppressed2 = new RuntimeException("Suppressed 2", suppressed1);
        final IllegalStateException nested2 = new IllegalStateException("Nested 2");
        nested2.addSuppressed(new RuntimeException("Nested 2a"));
        suppressed2.addSuppressed(nested2);
        suppressed2.addSuppressed(new IllegalStateException("Nested 2-2"));

        final RuntimeException suppressed3 = new RuntimeException("Suppressed 3");
        final IllegalStateException nested3 = new IllegalStateException("Nested 3");
        nested3.addSuppressed(new RuntimeException("Nested 3a"));
        suppressed3.addSuppressed(nested3);
        suppressed3.addSuppressed(new IllegalStateException("Nested 3-2"));

        final RuntimeException cause = new RuntimeException("This is the cause");
        cause.addSuppressed(suppressed1);
        cause.addSuppressed(suppressed2);
        cause.addSuppressed(suppressed3);
        return cause;
    }

    public static String formatMessage(final String msg, final Logger.Level level) {
        return msg + " - " + level.name().toLowerCase(Locale.ROOT);
    }
}
