/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * {@code HttpHandler} implementation checking whether the request can be accepted or not.
 *
 * @author Emanuel Muckenhuber
*/
class ManagementHttpRequestHandler implements HttpHandler {

    private final HttpHandler next;
    private final ManagementHttpRequestProcessor service;

    private final ExchangeCompletionListener listener = new ExchangeCompletionListener() {
        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            service.endRequest();
            nextListener.proceed();
        }
    };

    public ManagementHttpRequestHandler(ManagementHttpRequestProcessor service, HttpHandler next) {
        this.service = service;
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final boolean proceed = service.beginRequest();
        try {
            if (proceed) {
                next.handleRequest(exchange);
            } else {
                exchange.setStatusCode(503);
                exchange.endExchange();
            }
        } finally {
            if (proceed && (exchange.isComplete() || !exchange.isDispatched())) {
                service.endRequest();
            } else if (proceed) {
                exchange.addExchangeCompleteListener(listener);
            }
        }
    }

}
