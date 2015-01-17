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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.logging.Logger;
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
    private static final Logger LOGGER = Logger.getLogger(LoggingServiceActivator.class);

    @Override
    protected HttpHandler getHttpHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final Map<String, Deque<String>> params = exchange.getQueryParameters();
                String msg = DEFAULT_MESSAGE;
                if (params.containsKey(MSG_KEY)) {
                    msg = getFirstValue(params, MSG_KEY);
                }
                boolean includeLevel = false;
                if (params.containsKey(INCLUDE_LEVEL_KEY)) {
                    includeLevel = Boolean.parseBoolean(getFirstValue(params, INCLUDE_LEVEL_KEY));
                }
                boolean logInfoOnly = false;
                if (params.containsKey(LOG_INFO_ONLY_KEY)) {
                    logInfoOnly = Boolean.parseBoolean(getFirstValue(params, LOG_INFO_ONLY_KEY));
                }
                if (logInfoOnly) {
                    LOGGER.info(msg);
                } else {
                    for (Logger.Level level : LOG_LEVELS) {
                        if (includeLevel) {
                            LOGGER.log(level, formatMessage(msg, level));
                        } else {
                            LOGGER.log(level, msg);
                        }
                    }
                }
                exchange.getResponseSender().send("Response sent");
            }
        };
    }

    private String getFirstValue(final Map<String, Deque<String>> params, final String key) {
        if (params.containsKey(key)) {
            final Deque<String> values = params.get(key);
            if (values != null && !values.isEmpty()) {
                return values.getFirst();
            }
        }
        return null;
    }

    public static String formatMessage(final String msg, final Logger.Level level) {
        return msg + " - " + level.name().toLowerCase(Locale.ROOT);
    }
}
