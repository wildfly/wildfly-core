/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static org.jboss.as.domain.http.server.Common.METHOD_NOT_ALLOWED_HANDLER;
import static org.jboss.as.domain.http.server.Common.NOT_FOUND;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ManagementRootConsoleRedirectHandler implements HttpHandler {

    private static HttpString HTTP_GET = new HttpString("GET");
    private final ResourceHandlerDefinition consoleHandler;

    ManagementRootConsoleRedirectHandler(ResourceHandlerDefinition consoleHandler) {
        this.consoleHandler = consoleHandler;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (!exchange.getRequestMethod().equals(HTTP_GET)) {
            METHOD_NOT_ALLOWED_HANDLER.handleRequest(exchange);
            return;
        }

        if (consoleHandler != null && "/".equals(exchange.getRequestPath())) {
            consoleHandler.getHandler().handleRequest(exchange);
            return;
        }
        NOT_FOUND.handleRequest(exchange);
    }
}
