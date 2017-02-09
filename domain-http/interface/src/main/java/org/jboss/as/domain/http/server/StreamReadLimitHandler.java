/*
Copyright 2016 Red Hat, Inc.

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

import static org.jboss.as.domain.http.server.DomainUtil.getStreamIndex;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.util.Methods;

/**
 * Limits the number of concurrent GET requests that are reading management API stream responses.
 * This is based on Undertow's {@code RequestLimitHandler} class but is adapted to only limit GET
 * requests that use HTTP headers or URL query parameters that indicate a management stream's content
 * is wanted as the response entity.
 *
 * @author Brian Stansberry
 */
class StreamReadLimitHandler implements HttpHandler {

    // WFCORE-1777 Maximum number of worker threads that can be sending stream responses
    private static final int MAX_STREAM_SENDERS = 2;
    // WFCORE-1777 Maximum number of stream responses we are willing to queue up waiting for the sender
    private static final int MAX_STREAM_RESPONSES = 128;

    private final RequestLimit requestLimit;
    private final HttpHandler next;

    private StreamReadLimitHandler(HttpHandler next) {
        this.requestLimit = new RequestLimit(MAX_STREAM_SENDERS, MAX_STREAM_RESPONSES);
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (isStreamRead(exchange)) {
            requestLimit.handleRequest(exchange, next);
        } else {
            next.handleRequest(exchange);
        }
    }

    private static boolean isStreamRead(HttpServerExchange exchange) {
        return Methods.GET.equals(exchange.getRequestMethod())
                && getStreamIndex(exchange, exchange.getRequestHeaders()) > -1;
    }

    static HttpHandler wrap(HttpHandler next) {
        return new StreamReadLimitHandler(next);
    }
}
