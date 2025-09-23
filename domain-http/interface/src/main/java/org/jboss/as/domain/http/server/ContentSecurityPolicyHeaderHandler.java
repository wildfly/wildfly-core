/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public class ContentSecurityPolicyHeaderHandler implements HttpHandler {

    public static final String DEFAULT_CSP_HEADER = "default-src 'self';";
    private final HttpHandler next;

    public ContentSecurityPolicyHeaderHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().add(Headers.CONTENT_SECURITY_POLICY, DEFAULT_CSP_HEADER);
        if(this.next != null) {
            this.next.handleRequest(exchange);
        }
    }

}