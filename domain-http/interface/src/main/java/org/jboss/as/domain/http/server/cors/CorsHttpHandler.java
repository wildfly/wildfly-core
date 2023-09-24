/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server.cors;

import static io.undertow.server.handlers.ResponseCodeHandler.HANDLE_200;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_MAX_AGE;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.jboss.as.domain.http.server.cors.CorsUtil.isPreflightedRequest;
import static org.jboss.as.domain.http.server.cors.CorsUtil.matchOrigin;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Undertow handler for CORS headers.
 * @see http://www.w3.org/TR/cors
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class CorsHttpHandler implements HttpHandler {

    private final HttpHandler next;
    private final Collection<String> allowedOrigins = new ArrayList<String>();
    /** Default max age **/
    private static final long ONE_MINUTE_IN_SECONDS = 60;
    private static final long ONE_HOUR_IN_SECONDS = 60 * ONE_MINUTE_IN_SECONDS;

    public CorsHttpHandler(HttpHandler next, Collection<String> allowedOrigins) {
        this.next = next;
        if (allowedOrigins != null) {
            for (String allowedOrigin : allowedOrigins) {
                this.allowedOrigins.add(CorsUtil.sanitizeDefaultPort(allowedOrigin));
            }
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        HeaderMap headers = exchange.getRequestHeaders();
        if (CorsUtil.isCoreRequest(headers)) {
            if (isPreflightedRequest(exchange)) {
                handlePreflightRequest(exchange);
                return;
            }
            setCorsResponseHeaders(exchange);
        }
        next.handleRequest(exchange);
    }

    private void handlePreflightRequest(HttpServerExchange exchange) throws Exception {
        setCorsResponseHeaders(exchange);
        HANDLE_200.handleRequest(exchange);
    }

    private void setCorsResponseHeaders(HttpServerExchange exchange) throws Exception {
        HeaderMap headers = exchange.getRequestHeaders();
        if (headers.contains(Headers.ORIGIN)) {
            if(matchOrigin(exchange, allowedOrigins) != null) {
                exchange.getResponseHeaders().addAll(ACCESS_CONTROL_ALLOW_ORIGIN, headers.get(Headers.ORIGIN));
                exchange.getResponseHeaders().add(Headers.VARY, Headers.ORIGIN_STRING);
            }
        }
        HeaderValues requestedMethods = headers.get(ACCESS_CONTROL_REQUEST_METHOD);
        if (requestedMethods != null && !requestedMethods.isEmpty()) {
            exchange.getResponseHeaders().addAll(ACCESS_CONTROL_ALLOW_METHODS, requestedMethods);
        } else {
            exchange.getResponseHeaders().addAll(ACCESS_CONTROL_ALLOW_METHODS, Arrays.asList(new String[]{Methods.GET_STRING, Methods.POST_STRING}));
        }
        HeaderValues requestedHeaders = headers.get(ACCESS_CONTROL_REQUEST_HEADERS);
        if (requestedHeaders != null && !requestedHeaders.isEmpty()) {
            exchange.getResponseHeaders().addAll(ACCESS_CONTROL_ALLOW_HEADERS, requestedHeaders);
        } else {
            exchange.getResponseHeaders().add(ACCESS_CONTROL_ALLOW_HEADERS, Headers.CONTENT_TYPE_STRING);
            exchange.getResponseHeaders().add(ACCESS_CONTROL_ALLOW_HEADERS, Headers.WWW_AUTHENTICATE_STRING);
            exchange.getResponseHeaders().add(ACCESS_CONTROL_ALLOW_HEADERS, Headers.AUTHORIZATION_STRING);
        }
        exchange.getResponseHeaders().add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        exchange.getResponseHeaders().add(ACCESS_CONTROL_MAX_AGE, ONE_HOUR_IN_SECONDS);
    }
}
