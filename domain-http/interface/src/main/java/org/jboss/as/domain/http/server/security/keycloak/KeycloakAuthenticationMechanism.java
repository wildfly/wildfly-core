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

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.undertow.UndertowHttpFacade;
import org.keycloak.adapters.undertow.UndertowKeycloakAuthMech;
import org.keycloak.adapters.undertow.UndertowRequestAuthenticator;
import org.keycloak.adapters.undertow.UndertowUserSessionManagement;

/**
 * An extension of Keycloak's generic Undertow auth mechanism that knows how to
 * create a Keycloak UndertowRequestAuthenticator for WildFly's domain management.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class KeycloakAuthenticationMechanism extends UndertowKeycloakAuthMech {

    private static final int sslRedirectPort;
    static {
        String sslPort = System.getProperty("jboss.management.https.port", "9993");
        sslRedirectPort = Integer.parseInt(sslPort);
    }

    private final KeycloakDeployment deployment;
    private final UndertowUserSessionManagement userSessionManagement;

    public KeycloakAuthenticationMechanism(KeycloakDeployment deployment, UndertowUserSessionManagement userSessionManagement) {
        super(new AdapterDeploymentContext(deployment));
        this.deployment = deployment;
        this.userSessionManagement = userSessionManagement;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        UndertowHttpFacade facade = new UndertowHttpFacade(exchange);
        UndertowRequestAuthenticator authenticator = createRequestAuthenticator(exchange, securityContext, facade);
        return keycloakAuthenticate(exchange, securityContext, authenticator);
    }

    private UndertowRequestAuthenticator createRequestAuthenticator(HttpServerExchange exchange, SecurityContext securityContext, UndertowHttpFacade facade) {
        return new KeycloakRequestAuthenticator(facade, deployment, sslRedirectPort, securityContext, exchange, userSessionManagement);
    }

}
