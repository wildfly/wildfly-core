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

import static org.wildfly.test.security.common.ModelNodeUtil.setIfNotNull;

import org.jboss.dmr.ModelNode;

/**
 * Common parent for mechanism-realm-configuration and mechanism-configurations.
 *
 * @author Josef Cacek
 */
public class AbstractMechanismConfiguration {

    private final String preRealmPrincipalTransformer;
    private final String postRealmPrincipalTransformer;
    private final String finalPrincipalTransformer;
    private final String realmMapper;

    protected AbstractMechanismConfiguration(Builder<?> builder) {
        this.preRealmPrincipalTransformer = builder.preRealmPrincipalTransformer;
        this.postRealmPrincipalTransformer = builder.postRealmPrincipalTransformer;
        this.finalPrincipalTransformer = builder.finalPrincipalTransformer;
        this.realmMapper = builder.realmMapper;
    }

    public String getPreRealmPrincipalTransformer() {
        return preRealmPrincipalTransformer;
    }

    public String getPostRealmPrincipalTransformer() {
        return postRealmPrincipalTransformer;
    }

    public String getFinalPrincipalTransformer() {
        return finalPrincipalTransformer;
    }

    public String getRealmMapper() {
        return realmMapper;
    }

    protected ModelNode toModelNode() {
        final ModelNode node= new ModelNode();
        setIfNotNull(node, "pre-realm-principal-transformer", preRealmPrincipalTransformer);
        setIfNotNull(node, "post-realm-principal-transformer", postRealmPrincipalTransformer);
        setIfNotNull(node, "final-principal-transformer", finalPrincipalTransformer);
        setIfNotNull(node, "realm-mapper", realmMapper);
        return node;
    }

    /**
     * Builder to build {@link AbstractMechanismConfiguration}.
     */
    public abstract static class Builder<T extends Builder<T>> {
        private String preRealmPrincipalTransformer;
        private String postRealmPrincipalTransformer;
        private String finalPrincipalTransformer;
        private String realmMapper;

        protected Builder() {
        }

        protected abstract T self();

        public T withPreRealmPrincipalTransformer(String preRealmPrincipalTransformer) {
            this.preRealmPrincipalTransformer = preRealmPrincipalTransformer;
            return self();
        }

        public T withPostRealmPrincipalTransformer(String postRealmPrincipalTransformer) {
            this.postRealmPrincipalTransformer = postRealmPrincipalTransformer;
            return self();
        }

        public T withFinalPrincipalTransformer(String finalPrincipalTransformer) {
            this.finalPrincipalTransformer = finalPrincipalTransformer;
            return self();
        }

        public T withRealmMapper(String realmMapper) {
            this.realmMapper = realmMapper;
            return self();
        }
    }
}
