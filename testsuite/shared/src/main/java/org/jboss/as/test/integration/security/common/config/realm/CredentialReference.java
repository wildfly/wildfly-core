/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config.realm;


/**
 * A helper class to provide settings for credential reference.
 *
 * @author Martin Choma
 */
public class CredentialReference {

    private final String store;
    private final String alias;
    private final String type;
    private final String clearText;

    private CredentialReference(Builder builder) {
        this.store = builder.store;
        this.alias = builder.alias;
        this.type = builder.type;
        this.clearText = builder.clearText;
    }

    /**
     * Get the store
     *
     * @return the store
     */
    public String getStore() {
        return store;
    }

    /**
     * Get the alias
     *
     * @return alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Get the type
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Get the clearText
     *
     * @return the clearText
     */
    public String getClearText() {
        return clearText;
    }

    @Override
    public String toString() {
        return "CredentialReference [store=" + store + ", alias=" + alias + ", type=" + type + ", clearText=" + clearText + "]";
    }

    public static class Builder {

        private String store;
        private String alias;
        private String type;
        private String clearText;


        public Builder store(String store) {
            this.store = store;
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder clearText(String clearText) {
            this.clearText = clearText;
            return this;
        }

        public CredentialReference build() {
            return new CredentialReference(this);
        }
    }

}

