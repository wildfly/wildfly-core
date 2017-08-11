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

import java.util.Objects;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;

/**
 * Configuration for constant-realm-mapper Elytron resource.
 *
 * @author Josef Cacek
 */
public class ConstantRealmMapper extends AbstractConfigurableElement {

    private static final String CONSTANT_REALM_MAPPER = "constant-realm-mapper";
    private static final PathAddress PATH_ELYTRON = PathAddress.pathAddress().append("subsystem", "elytron");

    private final String realm;

    private ConstantRealmMapper(Builder builder) {
        super(builder);
        this.realm = Objects.requireNonNull(builder.realm, "Realm name must be provided");
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util.createAddOperation(PATH_ELYTRON.append(CONSTANT_REALM_MAPPER, name));
        ModelNode rolesNode = op.get("realm").set(realm);
        CoreUtils.applyUpdate(op, client);
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        CoreUtils.applyUpdate(Util.createRemoveOperation(PATH_ELYTRON.append(CONSTANT_REALM_MAPPER, name)), client);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for this class.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<ConstantRealmMapper.Builder> {

        private String realm;

        private Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ConstantRealmMapper build() {
            return new ConstantRealmMapper(this);
        }

        public Builder withRealm(String realm) {
            this.realm = realm;
            return this;
        }
    }
}
