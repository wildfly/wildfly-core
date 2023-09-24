/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config;

import java.net.URL;

/**
 * A simple config holder for JSSE keystore or truststore configuration in a security domain.
 *
 * @author Josef Cacek
 */
public class SecureStore {

    private final URL url;
    private final String password;
    private final String type;

    // Constructors ----------------------------------------------------------

    /**
     * Create a new SecureStore.
     *
     * @param builder
     */
    private SecureStore(Builder builder) {
        this.url = builder.url;
        this.password = builder.password;
        this.type = builder.type;
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the url.
     *
     * @return the url.
     */
    public URL getUrl() {
        return url;
    }

    /**
     * Get the password.
     *
     * @return the password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Get the type.
     *
     * @return the type.
     */
    public String getType() {
        return type;
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private URL url;
        private String password;
        private String type;

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public SecureStore build() {
            return new SecureStore(this);
        }
    }

}
