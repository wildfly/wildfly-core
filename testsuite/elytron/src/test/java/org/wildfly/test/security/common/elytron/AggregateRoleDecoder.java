/*
 * Copyright 2019 Red Hat, Inc.
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
