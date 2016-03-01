/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
