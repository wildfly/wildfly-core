/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;

/**
 * Configuration for source-address-role-decoder Elytron resource.
 */
public class SourceAddressRoleDecoder extends AbstractConfigurableElement {

    private static final String SOURCE_ADDRESS_ROLE_DECODER = "source-address-role-decoder";
    private static final PathAddress PATH_ELYTRON = PathAddress.pathAddress().append("subsystem", "elytron");
    private final String ipAddress;
    private final String role;

    private SourceAddressRoleDecoder(Builder builder) {
        super(builder);
        this.ipAddress = builder.ipAddress;
        this.role = builder.role;
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util.createAddOperation(PATH_ELYTRON.append(SOURCE_ADDRESS_ROLE_DECODER, name));
        op.get("source-address").set(ipAddress);
        op.get("roles").add(role);
        CoreUtils.applyUpdate(op, client);
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        CoreUtils.applyUpdate(Util.createRemoveOperation(PATH_ELYTRON.append(SOURCE_ADDRESS_ROLE_DECODER, name)), client);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for this class.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<SourceAddressRoleDecoder.Builder> {
        private String ipAddress;
        private String role;

        private Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        public SourceAddressRoleDecoder build() {
            return new SourceAddressRoleDecoder(this);
        }

        public Builder withIPAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder withRole(String role) {
            this.role = role;
            return this;
        }
    }
}
