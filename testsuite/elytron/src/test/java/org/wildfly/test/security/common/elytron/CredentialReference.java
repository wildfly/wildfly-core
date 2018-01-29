/*
 * Copyright 2017 Red Hat, Inc.
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

package org.wildfly.test.security.common.elytron;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.wildfly.test.security.common.ModelNodeUtil.setIfNotNull;

import org.jboss.dmr.ModelNode;

/**
 * Helper class for adding "credential-reference" attributes into CLI commands.
 *
 * @author Josef Cacek
 */
public class CredentialReference implements CliFragment, ModelNodeConvertable {

    public static final CredentialReference EMPTY = CredentialReference.builder().build();

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

    @Override
    public String asString() {
        StringBuilder sb = new StringBuilder();
        if (isNotBlank(alias) || isNotBlank(clearText) || isNotBlank(store) || isNotBlank(type)) {
            sb.append("credential-reference={ ");
            if (isNotBlank(alias)) {
                sb.append(String.format("alias=\"%s\", ", alias));
            }
            if (isNotBlank(store)) {
                sb.append(String.format("store=\"%s\", ", store));
            }
            if (isNotBlank(type)) {
                sb.append(String.format("type=\"%s\", ", type));
            }
            if (isNotBlank(clearText)) {
                sb.append(String.format("clear-text=\"%s\"", clearText));
            }
            sb.append("}, ");
        }
        return sb.toString();
    }

    @Override
    public ModelNode toModelNode() {
        if (this == EMPTY) {
            return null;
        }
        final ModelNode node= new ModelNode();
        setIfNotNull(node, "store", store);
        setIfNotNull(node, "alias", alias);
        setIfNotNull(node, "type", type);
        setIfNotNull(node, "clear-text", clearText);
        return node;
    }

    /**
     * Creates builder to build {@link CredentialReference}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link CredentialReference}.
     */
    public static final class Builder {
        private String store;
        private String alias;
        private String type;
        private String clearText;

        private Builder() {
        }

        public Builder withStore(String store) {
            this.store = store;
            return this;
        }

        public Builder withAlias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withClearText(String clearText) {
            this.clearText = clearText;
            return this;
        }

        public CredentialReference build() {
            return new CredentialReference(this);
        }
    }
}
