/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.jboss.as.domain.http.server.security.keycloak;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionManager;
import io.undertow.util.HeaderMap;
import static io.undertow.util.Headers.HOST;
import static io.undertow.util.Headers.LOCATION;
import static io.undertow.util.Headers.REFERER;
import io.undertow.util.Sessions;
import io.undertow.util.StatusCodes;
import java.net.URI;
import java.net.URISyntaxException;
import org.jboss.as.domain.http.server.ConsoleMode;
import org.jboss.as.domain.http.server.security.LogoutHandler;
import org.keycloak.adapters.undertow.KeycloakUndertowAccount;
import org.keycloak.adapters.undertow.UndertowUserSessionManagement;
import org.keycloak.util.KeycloakUriBuilder;

/**
 * Handles keycloak logout for Web Console.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class KeycloakLogoutHandler implements HttpHandler {

    private final UndertowUserSessionManagement userSessionManagement = KeycloakConfig.keycloakSessionManagement();
    private final SessionManager sessionManager = KeycloakConfig.sessionManager();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Session session = Sessions.getSession(exchange);
        if (session == null) {
            // Tried Keycloak logout from console.  But there was no Keycloak session.
            endRequest(exchange);
            return;
        }

        KeycloakUndertowAccount account = (KeycloakUndertowAccount)session.getAttribute(KeycloakUndertowAccount.class.getName());
        if (account == null) {
            //Account was not in session.  Just end request and return.
            endRequest(exchange);
            return;
        }

        String user = account.getPrincipal().getName();
        userSessionManagement.logoutUser(sessionManager, user);
        userSessionManagement.logoutKeycloakSession(sessionManager, user);
        endRequest(exchange);
    }

    private void endRequest(HttpServerExchange exchange) {
        final HeaderMap responseHeaders = exchange.getResponseHeaders();
        KeycloakUriBuilder logoutUrl = KeycloakConfig.WEB_CONSOLE.deployment().getLogoutUrl();
        logoutUrl = logoutUrl.queryParam("redirect_uri", redirectUri(exchange));
        responseHeaders.add(LOCATION, logoutUrl.build().toString());
        exchange.setResponseCode(StatusCodes.TEMPORARY_REDIRECT);
    }

    // code copied from org.jboss.as.domain.http.server.security.LogoutHandler
    private String redirectUri(HttpServerExchange exchange) {
        final HeaderMap requestHeaders = exchange.getRequestHeaders();
        final HeaderMap responseHeaders = exchange.getResponseHeaders();

        String referrer = responseHeaders.getFirst(REFERER);
        String protocol = exchange.getRequestScheme();
        String host = null;
        if (referrer != null) {
            try {
                URI uri = new URI(referrer);
                protocol = uri.getScheme();
                host = uri.getHost() + LogoutHandler.portPortion(protocol, uri.getPort());
            } catch (URISyntaxException e) {
            }
        }
        if (host == null) {
            host = requestHeaders.getFirst(HOST);
            if (host == null) {
                throw new IllegalStateException("Could not determine host for redirect after logout");
            }
        }

        return protocol + "://" + host + ConsoleMode.ConsoleHandler.CONTEXT;
    }

}
