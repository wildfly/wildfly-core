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
public class ConstantPermissionMapper extends AbstractConfigurableElement implements PermissionMapper {

    private static final String CONSTANT_PERMISSION_MAPPER = "constant-permission-mapper";
    private static final PathAddress PATH_ELYTRON = PathAddress.pathAddress().append("subsystem", "elytron");
    private final PermissionRef[] permissions;

    private ConstantPermissionMapper(Builder builder) {
        super(builder);
        this.permissions = builder.permissions;
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util.createAddOperation(PATH_ELYTRON.append(CONSTANT_PERMISSION_MAPPER, name));
        if (permissions != null) {
            ModelNode permissionsNode = op.get("permissions");
            for (PermissionRef permissionRef : permissions) {
                ModelNode permissionRefNode = new ModelNode();
                permissionRefNode.get("class-name").set(permissionRef.getClassName());
                setIfNotNull(permissionRefNode, "module", permissionRef.getModule());
                setIfNotNull(permissionRefNode, "target-name", permissionRef.getTargetName());
                setIfNotNull(permissionRefNode, "action", permissionRef.getAction());
                permissionsNode.add(permissionRefNode);
            }
        }
        CoreUtils.applyUpdate(op, client);
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        CoreUtils.applyUpdate(Util.createRemoveOperation(PATH_ELYTRON.append(CONSTANT_PERMISSION_MAPPER, name)), client);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for this class.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<ConstantPermissionMapper.Builder> {

        private PermissionRef[] permissions;

        private Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ConstantPermissionMapper build() {
            return new ConstantPermissionMapper(this);
        }

        public Builder withPermissions(PermissionRef... permissions) {
            this.permissions = permissions;
            return this;
        }
    }
}
