/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server.security;

import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.util.function.Function;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Filter to redirect to the error context while the security realm is not ready.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class RealmReadinessHandler implements HttpHandler {

    private final Function<HttpServerExchange, Boolean> readyFunction;
    private final HttpHandler next;

    RealmReadinessHandler(final Function<HttpServerExchange, Boolean> readyFunction, final HttpHandler next) {
        this.readyFunction = readyFunction;
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (readyFunction.apply(exchange)) {
            next.handleRequest(exchange);
        } else {
            try {
                rejectRequest(exchange);
            } catch (IOException e) {
                ROOT_LOGGER.error(e);
                exchange.setStatusCode(500);
                exchange.endExchange();
            }
        }
    }

    /**
     * Method to be implemented by sub classes to handle the rejection process due to the realm not being ready to authenticate
     * clients.
     * <p/>
     * Possible examples are sending a redirect to a page to inform the user that it is not possible due to no users being
     * defined or sending a DMR response indicating a failure.
     *
     * @param exchange
     * @throws IOException
     */
    abstract void rejectRequest(HttpServerExchange exchange) throws Exception;

}
