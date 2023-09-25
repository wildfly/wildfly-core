/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 * Helper abstract parent Elytron constant-* resources with one "constant" attribute.
 *
 * @author Josef Cacek
 */
public abstract class AbstractConstantHelper extends AbstractConfigurableElement {

    private final String constant;

    protected AbstractConstantHelper(Builder<?> builder) {
        super(builder);
        this.constant = checkNotNullParamWithNullPointerException("builder.constant", builder.constant);
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/%s=%s:add(constant=\"%s\")", getConstantElytronType(), name, constant));
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/%s=%s:remove()", getConstantElytronType(), name));
    }

    /**
     * Returns elytron node name (e.g. for resource /subsystem=elytron/constant-principal-transformer resources it returns
     * "constant-principal-transformer").
     */
    protected abstract String getConstantElytronType();

    /**
     * Builder to build {@link AbstractConstantHelper}.
     */
    public abstract static class Builder<T extends Builder<T>> extends AbstractConfigurableElement.Builder<T> {
        private String constant;

        protected Builder() {
        }

        public T withConstant(String constant) {
            this.constant = constant;
            return self();
        }
    }
}
