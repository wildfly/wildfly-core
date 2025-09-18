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
    private String headerContent = DEFAULT_CSP_HEADER;

    public ContentSecurityPolicyHeaderHandler(HttpHandler next, String headerContent) {
        super();
        this.next = next;
        this.headerContent = headerContent;
    }

    public ContentSecurityPolicyHeaderHandler(HttpHandler next) {
        this(next, DEFAULT_CSP_HEADER);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().add(Headers.CONTENT_SECURITY_POLICY, headerContent);
        if(this.next != null) {
            this.next.handleRequest(exchange);
        }
    }

}