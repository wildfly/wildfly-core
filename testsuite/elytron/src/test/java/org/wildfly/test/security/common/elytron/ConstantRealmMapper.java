/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

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
        this.realm = checkNotNullParamWithNullPointerException("builder.realm", builder.realm);
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util.createAddOperation(PATH_ELYTRON.append(CONSTANT_REALM_MAPPER, name));
        op.get("realm").set(realm);
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
