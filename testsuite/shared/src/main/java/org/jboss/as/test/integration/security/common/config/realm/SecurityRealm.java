/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config.realm;

/**
 * A helper class to provide settings for security realms.
 *
 * <pre>
 * /core-service=management/security-realm=ApplicationRealm:read-resource-description()
 * </pre>
 *
 * @author Josef Cacek
 */
public class SecurityRealm {

    private final String name;
    private final ServerIdentity serverIdentity;
    private final Authentication authentication;
    private final Authorization authorization;

    //    private final RealmAuthentication;

    // Constructors ----------------------------------------------------------

    private SecurityRealm(Builder builder) {
        this.name = builder.name;
        this.serverIdentity = builder.serverIdentity;
        this.authentication = builder.authentication;
        this.authorization = builder.authorization;
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the serverIdentity.
     *
     * @return the serverIdentity.
     */
    public ServerIdentity getServerIdentity() {
        return serverIdentity;
    }

    /**
     * Get the authentication.
     *
     * @return the authentication.
     */
    public Authentication getAuthentication() {
        return authentication;
    }

    /**
     * Get the authorization.
     *
     * @return the authorization.
     */
    public Authorization getAuthorization() {
        return authorization;
    }

    @Override
    public String toString() {
        return "SecurityRealm [name=" + name + ", serverIdentity=" + serverIdentity + ", authentication=" + authentication
                + ", authorization=" + authorization + "]";
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private String name;
        private ServerIdentity serverIdentity;
        private Authentication authentication;
        private Authorization authorization;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder serverIdentity(ServerIdentity serverIdentity) {
            this.serverIdentity = serverIdentity;
            return this;
        }

        public Builder authentication(Authentication authentication) {
            this.authentication = authentication;
            return this;
        }

        public Builder authorization(Authorization authorization) {
            this.authorization = authorization;
            return this;
        }

        public SecurityRealm build() {
            return new SecurityRealm(this);
        }
    }

}
