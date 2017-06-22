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
        this.constant = Objects.requireNonNull(builder.constant, "Constant has to be provided");
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
