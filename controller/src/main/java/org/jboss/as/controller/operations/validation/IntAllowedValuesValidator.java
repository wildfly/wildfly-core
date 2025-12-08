/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IntAllowedValuesValidator extends SetValidator<Integer> {

    public IntAllowedValuesValidator(int... values) {
        super(ModelType.INT, ModelNode::asIntOrNull, ModelNode::new, IntStream.of(values).boxed().collect(Collectors.toSet()));
    }
}
