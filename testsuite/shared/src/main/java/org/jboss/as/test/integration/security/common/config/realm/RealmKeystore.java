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
package org.jboss.as.test.integration.security.common.config.realm;

/**
 * A helper class to provide settings for SecurityRealm's keystore (SSL server-identity, or truststore authentication).
 *
 * <pre>
 * /core-service=management/security-realm=ApplicationRealm:read-resource-description(recursive=true)
 * </pre>
 *
 * @author Josef Cacek
 */
public class RealmKeystore {

    private final String keystorePath;
    private final String keystorePassword;
    private final String keyPassword;
    private final CredentialReference keystorePasswordCredentialReference;
    private final CredentialReference keyPasswordCredentialReference;

    // Constructors ----------------------------------------------------------

    /**
     * Construct instance from the Builder.
     *
     * @param builder
     */
    private RealmKeystore(Builder builder) {
        this.keystorePath = builder.keystorePath;
        this.keystorePassword = builder.keystorePassword;
        this.keyPassword = builder.keyPassword;
        this.keystorePasswordCredentialReference = builder.keystorePasswordCredentialReference;
        this.keyPasswordCredentialReference = builder.keyPasswordCredentialReference;
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the keystorePath.
     *
     * @return the keystorePath.
     */
    public String getKeystorePath() {
        return keystorePath;
    }

    /**
     * Get the keystorePassword.
     *
     * @return the keystorePassword.
     */
    public String getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * Get the keystorePasswordCredentialReference.
     *
     * @return the keystorePasswordCredentialReference
     */
    public CredentialReference getKeystorePasswordCredentialReference() {
        return keystorePasswordCredentialReference;
    }

    /**
     * Get the keyPassword.
     *
     * @return the keyPassword.
     */
    public String getKeyPassword() {
        return keyPassword;
    }

    /**
     * Get the keyPasswordCredentialReference.
     *
     * @return the keyPasswordCredentialReference
     */
    public CredentialReference getKeyPasswordCredentialReference() {
        return keyPasswordCredentialReference;
    }

    @Override
    public String toString() {
        return "RealmKeystore [keystorePath=" + keystorePath + ", keystorePassword=" + keystorePassword + ", keystorePasswordCredentialReference=" + keystorePasswordCredentialReference + ", keyPassword=" + keyPassword + ", keyPasswordCredentialReference=" + keyPasswordCredentialReference + "]";
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private String keystorePath;
        private String keystorePassword;
        private String keyPassword;
        private CredentialReference keystorePasswordCredentialReference;
        private CredentialReference keyPasswordCredentialReference;

        public Builder keystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
            return this;
        }

        public Builder keystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
            return this;
        }

        public Builder keystorePasswordCredentialReference(CredentialReference keystorePasswordCredentialReference) {
            this.keystorePasswordCredentialReference = keystorePasswordCredentialReference;
            return this;
        }

        public Builder keyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
            return this;
        }

        public Builder keyPasswordCredentialReference(CredentialReference keyPasswordCredentialReference) {
            this.keyPasswordCredentialReference = keyPasswordCredentialReference;
            return this;
        }

        public RealmKeystore build() {
            return new RealmKeystore(this);
        }
    }

}
