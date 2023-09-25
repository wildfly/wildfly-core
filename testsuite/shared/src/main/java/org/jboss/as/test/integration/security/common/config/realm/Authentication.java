/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config.realm;

/**
 * A helper class to provide settings for SecurityRealm's authentication.
 *
 * @author Josef Cacek
 */
public class Authentication {

    private final RealmKeystore truststore;
    private final LdapAuthentication ldap;

    // Constructors ----------------------------------------------------------

    private Authentication(Builder builder) {
        this.truststore = builder.truststore;
        this.ldap = builder.ldap;
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the truststore.
     *
     * @return the truststore.
     */
    public RealmKeystore getTruststore() {
        return truststore;
    }

    /**
     * Get the ldap.
     *
     * @return the ldap.
     */
    public LdapAuthentication getLdap() {
        return ldap;
    }

    @Override
    public String toString() {
        return "Authentication [truststore=" + truststore + "]";
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private RealmKeystore truststore;
        private LdapAuthentication ldap;

        public Builder truststore(RealmKeystore truststore) {
            this.truststore = truststore;
            return this;
        }

        public Builder ldap(LdapAuthentication ldap) {
            this.ldap = ldap;
            return this;
        }

        public Authentication build() {
            return new Authentication(this);
        }
    }

}
