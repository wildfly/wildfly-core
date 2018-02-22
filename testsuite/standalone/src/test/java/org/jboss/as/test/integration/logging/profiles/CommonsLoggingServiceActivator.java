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

package org.jboss.as.test.integration.logging.profiles;

import java.util.Deque;
import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wildfly.test.undertow.UndertowServiceActivator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CommonsLoggingServiceActivator extends UndertowServiceActivator {
    public static final String DEFAULT_MESSAGE = "Default log message";
    private static final Log LOGGER = LogFactory.getLog(CommonsLoggingServiceActivator.class);

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
                LOGGER.fatal(msg);
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
