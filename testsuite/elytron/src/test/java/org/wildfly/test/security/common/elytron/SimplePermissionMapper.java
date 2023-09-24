/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;

/**
 * Configuration for simple-permission-mapper Elytron resource.
 */
public class SimplePermissionMapper extends AbstractConfigurableElement implements PermissionMapper {

    private static final String SIMPLE_PERMISSION_MAPPER = "simple-permission-mapper";
    private static final PathAddress PATH_ELYTRON = PathAddress.pathAddress().append("subsystem", "elytron");
    private final List<Mapping> mappingList;
    private final MappingMode mappingMode;

    private SimplePermissionMapper(Builder builder) {
        super(builder);
        this.mappingList = builder.mappingList;
        this.mappingMode = builder.mappingMode;
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util.createAddOperation(PATH_ELYTRON.append(SIMPLE_PERMISSION_MAPPER, name));
        if (mappingList != null && ! mappingList.isEmpty()) {
            for (Mapping mapping : mappingList) {
                ModelNode permissionMapping = new ModelNode();
                for (String role : mapping.roles) {
                    permissionMapping.get("roles").add(role);
                }
                for (String principal : mapping.principals) {
                    permissionMapping.get("principals").add(principal);
                }
                for (String permissionSetName : mapping.permissionSets) {
                    ModelNode permissionSet = new ModelNode();
                    permissionSet.get("permission-set").set(permissionSetName);
                    permissionMapping.get("permission-sets").add(permissionSet);
                }
                op.get("permission-mappings").add(permissionMapping);
                op.get("mapping-mode").set(mappingMode.toString());
            }
            CoreUtils.applyUpdate(op, client);
        }

    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        CoreUtils.applyUpdate(Util.createRemoveOperation(PATH_ELYTRON.append(SIMPLE_PERMISSION_MAPPER, name)), client);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for this class.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<SimplePermissionMapper.Builder> {
        private List<Mapping> mappingList = new ArrayList<>();
        private MappingMode mappingMode;

        private Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        public SimplePermissionMapper build() {
            return new SimplePermissionMapper(this);
        }

        public Builder withMapping(Set<String> principals, Set<String> roles, Set<String> permissionSets) {
            mappingList.add(new Mapping(principals, roles, permissionSets));
            return this;
        }

        public Builder withMappingMode(MappingMode mappingMode) {
            this.mappingMode = mappingMode;
            return this;
        }
    }

    private static final class Mapping {
        final Set<String> principals;
        final Set<String> roles;
        final Set<String> permissionSets;

        Mapping(Set<String> principals, Set<String> roles, Set<String> permissionSets) {
            this.principals = principals;
            this.roles = roles;
            this.permissionSets = permissionSets;
        }
    }
}
