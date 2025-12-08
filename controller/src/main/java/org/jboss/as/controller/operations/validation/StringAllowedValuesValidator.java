/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import java.util.Set;
import java.util.function.Function;

public class StringAllowedValuesValidator extends SetValidator<String> {

    public StringAllowedValuesValidator(String... values) {
        super(Function.identity(), Set.of(values));
    }
}
