/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

import java.util.concurrent.Executor;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/** Handler useful for wrapping calls in custom executors such as Management executor outside worker
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
final class InExecutorHandler implements HttpHandler {
    private Executor executor;
    private HttpHandler next;

    InExecutorHandler(Executor executor, HttpHandler next) {
        this.executor = executor;
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
        httpServerExchange.dispatch(executor, next);
    }

    static HttpHandler wrap(Executor executor, HttpHandler next){
        return new InExecutorHandler(executor, next);
    }
}
