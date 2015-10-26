/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
