/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.domain.http.server.cors;

import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ORIGIN;
import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.util.Collection;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class CorsUtil {

    public static boolean isCoreRequest(HeaderMap headers) {
        return headers.contains(ORIGIN)
                || headers.contains(ACCESS_CONTROL_REQUEST_HEADERS)
                || headers.contains(ACCESS_CONTROL_REQUEST_METHOD);
    }

    /**
     * Match the Origin header with the allowed origins. If it doesn't match then a 403 response code is set on the response.
     * @param exchange the current HttpExchange.
     * @param allowedOrigins list of allowed origins.
     * @return the matched origin, null otherwise.
     * @throws Exception
     */
    public static String matchOrigin(HttpServerExchange exchange, Collection<String> allowedOrigins) throws Exception {
        HeaderMap headers = exchange.getRequestHeaders();
        String origin = headers.getFirst(Headers.ORIGIN);
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            for (String allowedOrigin : allowedOrigins) {
                if (allowedOrigin.equalsIgnoreCase(origin)) {
                    return origin;
                }
            }
        }
        String allowedOrigin = defaultOrigin(exchange);
        if (allowedOrigin.equalsIgnoreCase(origin)) {
            return origin;
        }
        ROOT_LOGGER.debug("Request rejected due to HOST/ORIGIN mis-match.");
        ResponseCodeHandler.HANDLE_403.handleRequest(exchange);
        return null;
    }

    public static String defaultOrigin(HttpServerExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst(Headers.HOST);
        String protocol = exchange.getRequestScheme();
        int port = exchange.getHostPort();
        //This browser set header should not need IPv6 escaping
        StringBuilder allowedOrigin = new StringBuilder(256);
        allowedOrigin.append(protocol).append("://").append(host);
        if (!isDefaultPort(port, protocol)) {
            allowedOrigin.append(':').append(port);
        }
        return allowedOrigin.toString();
    }

    private static boolean isDefaultPort(int port, String protocol) {
        return (("http".equals(protocol) && 80 == port) || ("https".equals(protocol) && 443 == port));
    }

    public static boolean isPreflightedRequest(HttpServerExchange exchange) {
        return Methods.OPTIONS.equals(exchange.getRequestMethod()) && isCoreRequest(exchange.getRequestHeaders());
    }
}
