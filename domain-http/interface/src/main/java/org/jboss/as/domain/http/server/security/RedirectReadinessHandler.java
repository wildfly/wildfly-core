/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server.security;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.util.function.Function;

import org.jboss.as.domain.http.server.Common;

/**
 * A readiness filter to redirect users to an error page if the realm is not ready to handle authentication requests.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RedirectReadinessHandler extends RealmReadinessHandler {

    private final String redirectTo;

    public RedirectReadinessHandler(final Function<HttpServerExchange, Boolean> readyFunction, final HttpHandler next, final String redirectTo) {
        super(readyFunction, next);
        this.redirectTo = redirectTo;
    }

    /**
     * @see RealmReadinessHandler#rejectRequest(HttpServerExchange)
     */
    @Override
    void rejectRequest(HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().add(Headers.LOCATION, redirectTo);

        Common.TEMPORARY_REDIRECT.handleRequest(exchange);
    }
}