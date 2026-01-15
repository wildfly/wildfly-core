/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates that a parameter values matches the string representation of a member of an enum set.
 * @author Paul Ferraro
 */
public class EnumStringSetValidator<E extends Enum<E>> extends SetValidator<String> {

    /**
     * Creates a validator that requires a parameter value match the string representation of an enum member of the specified type.
     * @param enumType an enum type
     */
    public EnumStringSetValidator(Class<E> enumType) {
        this(EnumSet.allOf(enumType));
    }

    /**
     * Creates a validator that requires a parameter value match the string representation of an enum member of the specified set.
     * @param allowed an enum set
     */
    public EnumStringSetValidator(Set<E> allowed) {
        super(Function.identity(), allowed.stream().map(Object::toString).collect(Collectors.toSet()));
    }
}
