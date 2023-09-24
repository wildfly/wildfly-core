/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.suspendresumeendpoint;

import java.util.concurrent.CountDownLatch;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class SuspendResumeHandler implements HttpHandler {
    public volatile CountDownLatch requestLatch = new CountDownLatch(1);
    public static final String TEXT = "Running Request";

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if(exchange.getQueryParameters().containsKey(TestUndertowService.SKIP_GRACEFUL)) {
            //invoke the latch in a completion listener, to make sure the response is sent
            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                @Override
                public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                    requestLatch.countDown();
                    nextListener.proceed();
                }
            });
            return;
        }

        try {
            requestLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        exchange.getResponseSender().send(TEXT);
    }
}
