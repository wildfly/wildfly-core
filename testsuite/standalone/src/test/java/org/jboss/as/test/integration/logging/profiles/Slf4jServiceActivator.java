/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.profiles;

import java.util.Deque;
import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.test.undertow.UndertowServiceActivator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Slf4jServiceActivator extends UndertowServiceActivator {
    public static final String DEFAULT_MESSAGE = "Default log message";
    private static final Logger LOGGER = LoggerFactory.getLogger(Slf4jServiceActivator.class);

    @Override
    protected HttpHandler getHttpHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                final Map<String, Deque<String>> params = exchange.getQueryParameters();
                String msg = DEFAULT_MESSAGE;
                if (params.containsKey("msg")) {
                    msg = getFirstValue(params, "msg");
                }
                // Log all levels
                LOGGER.trace(msg);
                LOGGER.debug(msg);
                LOGGER.info(msg);
                LOGGER.warn(msg);
                LOGGER.error(msg);
                //LOGGER.fatal(msg);
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
}
