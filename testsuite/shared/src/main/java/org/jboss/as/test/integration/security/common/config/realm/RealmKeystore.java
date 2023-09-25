/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
    private final String alias;
    private final String provider;

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
        this.alias = builder.alias;
        this.provider = builder.provider;
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

    /**
     * Getter for the alias.
     *
     * @return the alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Getter for the provider.
     *
     * @return the alias
     */
    public String getProvider() {
        return provider;
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
        private String alias;
        private String provider;

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

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public RealmKeystore build() {
            return new RealmKeystore(this);
        }
    }

}
