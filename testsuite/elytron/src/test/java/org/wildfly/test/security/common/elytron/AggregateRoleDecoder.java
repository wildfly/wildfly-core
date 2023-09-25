/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;

/**
 * Configuration for aggregate-role-decoder Elytron resource.
 */
public class AggregateRoleDecoder extends AbstractConfigurableElement {

    private static final String AGGREGATE_ROLE_DECODER = "aggregate-role-decoder";
    private static final PathAddress PATH_ELYTRON = PathAddress.pathAddress().append("subsystem", "elytron");
    private List<String> roleDecoders;

    private AggregateRoleDecoder(Builder builder) {
        super(builder);
        this.roleDecoders = builder.roleDecoders;
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util.createAddOperation(PATH_ELYTRON.append(AGGREGATE_ROLE_DECODER, name));
        if (roleDecoders != null && ! roleDecoders.isEmpty()) {
            for (String roleDecoder : roleDecoders) {
                op.get("role-decoders").add(roleDecoder);
            }
            CoreUtils.applyUpdate(op, client);
        }

    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        CoreUtils.applyUpdate(Util.createRemoveOperation(PATH_ELYTRON.append(AGGREGATE_ROLE_DECODER, name)), client);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for this class.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<AggregateRoleDecoder.Builder> {
        private List<String> roleDecoders = new ArrayList<>();

        private Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        public AggregateRoleDecoder build() {
            return new AggregateRoleDecoder(this);
        }

        public Builder withRoleDecoder(String roleDecoder) {
            roleDecoders.add(roleDecoder);
            return this;
        }
    }
}
