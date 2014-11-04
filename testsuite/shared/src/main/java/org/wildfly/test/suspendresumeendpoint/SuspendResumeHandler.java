/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
