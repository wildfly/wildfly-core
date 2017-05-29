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
 * Configuration for constant-permission-mapper Elytron resource.
 *
 * @author Josef Cacek
 */
public class ConstantRoleMapper extends AbstractConfigurableElement {

    private static final String CONSTANT_ROLE_MAPPER = "constant-role-mapper";
    private static final PathAddress PATH_ELYTRON = PathAddress.pathAddress().append("subsystem", "elytron");

    private final String[] roles;

    private ConstantRoleMapper(Builder builder) {
        super(builder);
        this.roles = Objects.requireNonNull(builder.roles, "Roles must be provided");
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util.createAddOperation(PATH_ELYTRON.append(CONSTANT_ROLE_MAPPER, name));
        ModelNode rolesNode = op.get("roles");
        for (String role : roles) {
            rolesNode.add(role);
        }
        CoreUtils.applyUpdate(op, client);
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        CoreUtils.applyUpdate(Util.createRemoveOperation(PATH_ELYTRON.append(CONSTANT_ROLE_MAPPER, name)), client);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for this class.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<ConstantRoleMapper.Builder> {

        private String[] roles;

        private Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ConstantRoleMapper build() {
            return new ConstantRoleMapper(this);
        }

        public Builder withRoles(String... roles) {
            this.roles = roles;
            return this;
        }
    }
}
