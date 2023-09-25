/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config.realm;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A helper class to provide settings for SecurityRealm's server-identity.
 *
 * @author Josef Cacek
 */
public class ServerIdentity {

    //Configuration of the secret/password-based identity of a server or host controller.
    private final String secret;
    private final RealmKeystore ssl;

    // Constructors ----------------------------------------------------------

    private ServerIdentity(Builder builder) {
        this.secret = builder.secret;
        this.ssl = builder.ssl;
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the secret.
     *
     * @return the secret.
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Get the serverIdentitySSL.
     *
     * @return the serverIdentitySSL.
     */
    public RealmKeystore getSsl() {
        return ssl;
    }

    @Override
    public String toString() {
        return "ServerIdentity [secret=" + secret + ", ssl=" + ssl + "]";
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private String secret;
        private RealmKeystore ssl;

        public Builder secretBase64(String base64secret) {
            this.secret = base64secret;
            return this;
        }

        public Builder ssl(RealmKeystore ssl) {
            this.ssl = ssl;
            return this;
        }

        public Builder secretPlain(String plainSecret) {
            if (plainSecret == null) {
                this.secret = null;
                return this;
            }

            byte[] secretBytes = plainSecret.getBytes(StandardCharsets.UTF_8);
            this.secret = Base64.getEncoder().encodeToString(secretBytes);
            return this;
        }

        public ServerIdentity build() {
            return new ServerIdentity(this);
        }
    }

}
