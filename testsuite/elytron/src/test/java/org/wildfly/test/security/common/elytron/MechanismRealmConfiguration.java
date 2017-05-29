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

import java.util.Objects;

import org.jboss.dmr.ModelNode;

/**
 * Object which holds single instance from mechanism-realm-configurations list.
 *
 * @author Josef Cacek
 */
public class MechanismRealmConfiguration extends AbstractMechanismConfiguration {

    private final String realmName;

    private MechanismRealmConfiguration(Builder builder) {
        super(builder);
        this.realmName = Objects.requireNonNull(builder.realmName, "Realm name must not be null.");
    }

    public String getRealmName() {
        return realmName;
    }

    /**
     * Creates builder to build {@link MechanismRealmConfiguration}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }


    @Override
    public ModelNode toModelNode() {
        ModelNode node = super.toModelNode();
        setIfNotNull(node, "realm-name", realmName);
        return node;
    }

    /**
     * Builder to build {@link MechanismRealmConfiguration}.
     */
    public static final class Builder extends AbstractMechanismConfiguration.Builder<Builder> {
        private String realmName;

        private Builder() {
        }

        public Builder withRealmName(String realmName) {
            this.realmName = realmName;
            return this;
        }

        public MechanismRealmConfiguration build() {
            return new MechanismRealmConfiguration(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

}
