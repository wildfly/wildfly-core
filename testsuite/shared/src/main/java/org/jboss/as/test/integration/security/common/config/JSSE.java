/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config;


/**
 * A simple config holder for JSSE configuration in a security domain.
 *
 * @author Josef Cacek
 */
public class JSSE {

    private final SecureStore keyStore;
    private final SecureStore trustStore;

    // Constructors ----------------------------------------------------------

    /**
     * Create a new JSSE.
     *
     * @param builder
     */
    private JSSE(Builder builder) {
        this.keyStore = builder.keyStore;
        this.trustStore = builder.trustStore;
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the keyStore.
     *
     * @return the keyStore.
     */
    public SecureStore getKeyStore() {
        return keyStore;
    }

    /**
     * Get the trustStore.
     *
     * @return the trustStore.
     */
    public SecureStore getTrustStore() {
        return trustStore;
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private SecureStore keyStore;
        private SecureStore trustStore;

        public Builder keyStore(SecureStore keyStore) {
            this.keyStore = keyStore;
            return this;
        }

        public Builder trustStore(SecureStore trustStore) {
            this.trustStore = trustStore;
            return this;
        }

        public JSSE build() {
            return new JSSE(this);
        }
    }

}
