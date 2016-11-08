/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.http.server.security;

import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.util.function.Function;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Filter to redirect to the error context while the security realm is not ready.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class RealmReadinessHandler implements HttpHandler {

    private final Function<HttpServerExchange, Boolean> readyFunction;
    private final HttpHandler next;

    RealmReadinessHandler(final Function<HttpServerExchange, Boolean> readyFunction, final HttpHandler next) {
        this.readyFunction = readyFunction;
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (readyFunction.apply(exchange)) {
            next.handleRequest(exchange);
        } else {
            try {
                rejectRequest(exchange);
            } catch (IOException e) {
                ROOT_LOGGER.error(e);
                exchange.setStatusCode(500);
                exchange.endExchange();
            }
        }
    }

    /**
     * Method to be implemented by sub classes to handle the rejection process due to the realm not being ready to authenticate
     * clients.
     * <p/>
     * Possible examples are sending a redirect to a page to inform the user that it is not possible due to no users being
     * defined or sending a DMR response indicating a failure.
     *
     * @param exchange
     * @throws IOException
     */
    abstract void rejectRequest(HttpServerExchange exchange) throws Exception;

}
