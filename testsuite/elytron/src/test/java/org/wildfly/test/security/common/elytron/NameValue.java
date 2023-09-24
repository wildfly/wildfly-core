/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import org.jboss.dmr.ModelNode;

/**
 * Representation of name and value attribute pair in domain model.
 *
 * @author Josef Cacek
 */
public class NameValue implements ModelNodeConvertable {

    private final String name;
    private final String value;

    private NameValue(Builder builder) {
        this.name = checkNotNullParamWithNullPointerException("builder.name", builder.name);
        this.value = checkNotNullParamWithNullPointerException("builder.value", builder.value);
    }

    @Override
    public ModelNode toModelNode() {
        final ModelNode node = new ModelNode();
        node.get("name").set(name);
        node.get("value").set(value);
        return null;
    }

    /**
     * Creates builder to build {@link NameValue}.
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static NameValue from(String name, String value) {
        return builder().withName(name).withValue(value).build();
    }

    /**
     * Builder to build {@link NameValue}.
     */
    public static final class Builder {
        private String name;
        private String value;

        private Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withValue(String value) {
            this.value = value;
            return this;
        }

        public NameValue build() {
            return new NameValue(this);
        }
    }
}
