/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging;

import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.ERROR;
import static org.apache.logging.log4j.Level.FATAL;
import static org.apache.logging.log4j.Level.INFO;
import static org.apache.logging.log4j.Level.TRACE;
import static org.apache.logging.log4j.Level.WARN;

import java.util.Deque;
import java.util.Locale;
import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wildfly.test.undertow.UndertowServiceActivator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Log4j2ServiceActivator extends UndertowServiceActivator {
    public static final String DEFAULT_MESSAGE = "Default log message";
    public static final Level[] LOG_LEVELS = {DEBUG, TRACE, INFO, WARN, ERROR, FATAL};
    private static final Logger LOGGER = LogManager.getLogger(Log4j2ServiceActivator.class);

    @Override
    protected HttpHandler getHttpHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final Map<String, Deque<String>> params = exchange.getQueryParameters();
                String msg = DEFAULT_MESSAGE;
                if (params.containsKey("msg")) {
                    msg = getFirstValue(params, "msg");
                }
                boolean includeLevel = false;
                if (params.containsKey("includeLevel")) {
                    includeLevel = Boolean.parseBoolean(getFirstValue(params, "includeLevel"));
                }
                for (Level level : LOG_LEVELS) {
                    if (includeLevel) {
                        LOGGER.log(level, formatMessage(msg, level));
                    } else {
                        LOGGER.log(level, msg);
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

    public static String formatMessage(final String msg, final Level level) {
        return msg + " - " + level.toString().toLowerCase(Locale.ROOT);
    }
}
