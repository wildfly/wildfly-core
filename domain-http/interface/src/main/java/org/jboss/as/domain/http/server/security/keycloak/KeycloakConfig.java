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

import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.undertow.UndertowUserSessionManagement;
import org.keycloak.subsystem.extension.KeycloakAdapterConfigService;

/**
 * Keycloak deployment configurations needed for web console.  The deployment configuration
 * may come from the Keycloak subsystem or from a json file in the configuration directory.
 *
 * Subsystem configuration takes precedence.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public enum KeycloakConfig {
    HTTP_ENDPOINT("http-endpoint"),
    WEB_CONSOLE("web-console");

    // web-console and http-endpoint share the same SessionManager, SessionConfig, and UndertowUserSessionManagement
    private final SessionManager sessionManager;
    private final SessionConfig sessionConfig;
    private final UndertowUserSessionManagement keycloakSessionManagement;

    private final KeycloakDeployment deployment;
    private final AdapterDeploymentContext context;

    KeycloakConfig(String keycloakSecureDeploymentName) {
        String subsysConfig = getDeploymentJSONFromSubsys(keycloakSecureDeploymentName);

        if (subsysConfig == null) {
            throw new IllegalStateException("Unable to locate Keycloak adapter configuration for " + keycloakSecureDeploymentName);
        }

        this.deployment = KeycloakDeploymentBuilder.build(configStreamFromJSON(subsysConfig));
        this.context = new AdapterDeploymentContext(deployment);

        // only initialize these for http-endpoint.  web-console will use the same instances.
        // we can't use a shared static member because it might leak sessions on :reload
        if (keycloakSecureDeploymentName.equals("http-endpoint")) {
            this.sessionManager = new InMemorySessionManager("http-endpoint", 200);
            this.sessionConfig = new SessionCookieConfig();
            this.keycloakSessionManagement = new UndertowUserSessionManagement();
        } else {
            this.sessionManager = null;
            this.sessionConfig = null;
            this.keycloakSessionManagement = null;
        }
    }

    /**
     * Get the KeycloakDeployment config.
     *
     * @return The deployment config.
     */
    public KeycloakDeployment deployment() {
        return this.deployment;
    }

    /**
     * Get the AdapterDeploymentContext.
     *
     * @return The context.
     */
    public AdapterDeploymentContext context() {
        return this.context;
    }

    public static SessionManager sessionManager() {
        return HTTP_ENDPOINT.sessionManager;
    }

    public static SessionConfig sessionConfig() {
        return HTTP_ENDPOINT.sessionConfig;
    }

    public static UndertowUserSessionManagement keycloakSessionManagement() {
        return HTTP_ENDPOINT.keycloakSessionManagement;
    }

    private static InputStream configStreamFromJSON(String json) {
        return new ByteArrayInputStream(json.getBytes());
    }

    // Attempt to get the Keycloak config from the Keycloak Subsystem.
    // If deployment not defined, return null.
    private static String getDeploymentJSONFromSubsys(String deploymentName) {
        if (!KeycloakAdapterConfigService.INSTANCE.isKeycloakDeployment(deploymentName)) return null;
        return KeycloakAdapterConfigService.INSTANCE.getJSON(deploymentName);
    }
}
