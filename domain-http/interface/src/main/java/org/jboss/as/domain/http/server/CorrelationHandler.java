/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * For any request with an X-Correlation-ID request header,
 * adds the same header (and value) to the response headers.
 *
 * @author Heiko Braun
 */
class CorrelationHandler implements HttpHandler {

    private static final HttpString HEADER = new HttpString("X-Correlation-ID");

    private final HttpHandler next;

    private CorrelationHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String corr = exchange.getRequestHeaders().getFirst(HEADER);
        if(corr != null) {
            exchange.getResponseHeaders().put(HEADER, corr);
        }
        next.handleRequest(exchange);

    }

    static HttpHandler wrap(HttpHandler next) {
        return new CorrelationHandler(next);
    }
}
