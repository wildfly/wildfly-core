/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
