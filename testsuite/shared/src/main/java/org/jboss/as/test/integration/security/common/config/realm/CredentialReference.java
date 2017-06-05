/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

