/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.EnumSet;
import java.util.Set;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates that a parameter can be resolved to a member of an enum set using standard enum resolution.
 * @author Paul Ferraro
 */
public class EnumSetValidator<E extends Enum<E>> extends SetValidator<E> {

    /**
     * Creates a validator that requires a parameter value resolve to an enum member of the specified type.
     * @param enumType an enum type
     */
    public EnumSetValidator(Class<E> enumType) {
        this(enumType, EnumSet.allOf(enumType));
    }

    /**
     * Creates a validator that requires a parameter value resolve to an enum member of the specified set.
     * @param allowed an enum set
     */
    public EnumSetValidator(Set<E> allowed) {
        this((allowed.isEmpty() ? EnumSet.complementOf(EnumSet.copyOf(allowed)) : allowed).iterator().next().getDeclaringClass(), allowed);
    }

    private EnumSetValidator(Class<E> enumType, Set<E> allowed) {
        super(ModelType.STRING, value -> Enum.valueOf(enumType, value.asString()), value -> new ModelNode(value.name()), allowed);
    }
}
