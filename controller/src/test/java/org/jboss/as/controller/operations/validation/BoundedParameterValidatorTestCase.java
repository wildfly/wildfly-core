/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import static org.assertj.core.api.Assertions.*;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 * Unit test for {@link BoundedParameterValidator}.
 * @author Paul Ferraro
 */
public class BoundedParameterValidatorTestCase {

    @Test
    public void illegal() {
        // Upper bound must be greater than lower bound
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(()  -> BoundedParameterValidator.integerBuilder().withLowerBound(Bound.inclusive(1)).withUpperBound(Bound.inclusive(0)).build());
        // Lower bound must be less than upper bound
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(()  -> BoundedParameterValidator.integerBuilder().withUpperBound(Bound.inclusive(0)).withLowerBound(Bound.inclusive(1)).build());
        // If lower and upper bounds are the same, they must be inclusive
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(()  -> BoundedParameterValidator.integerBuilder().withLowerBound(Bound.exclusive(0)).withUpperBound(Bound.exclusive(0)).build());
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(()  -> BoundedParameterValidator.integerBuilder().withLowerBound(Bound.inclusive(0)).withUpperBound(Bound.exclusive(0)).build());
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(()  -> BoundedParameterValidator.integerBuilder().withLowerBound(Bound.exclusive(0)).withUpperBound(Bound.inclusive(0)).build());
    }

    @Test
    public void unresolvable() {
        BoundedParameterValidator<Integer> validator = BoundedParameterValidator.builder(ModelType.STRING, ModelNode::asIntOrNull).build();
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode("foo")));
    }

    @Test
    public void test() {
        BoundedParameterValidator<Integer> validator = BoundedParameterValidator.integerBuilder().build();
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", new ModelNode(-1)));
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", ModelNode.ZERO));
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", new ModelNode(1)));
    }

    @Test
    public void lowerExclusive() {
        BoundedParameterValidator<Integer> validator = BoundedParameterValidator.integerBuilder().withLowerBound(Bound.exclusive(0)).build();
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode(-1)));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", ModelNode.ZERO));
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", new ModelNode(1)));
    }

    @Test
    public void lowerInclusive() {
        BoundedParameterValidator<Integer> validator = BoundedParameterValidator.integerBuilder().withLowerBound(Bound.inclusive(0)).build();
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode(-1)));
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", ModelNode.ZERO));
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", new ModelNode(1)));
    }

    @Test
    public void upperExclusive() {
        BoundedParameterValidator<Integer> validator = BoundedParameterValidator.integerBuilder().withUpperBound(Bound.exclusive(0)).build();
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", new ModelNode(-1)));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", ModelNode.ZERO));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode(1)));
    }

    @Test
    public void upperInclusive() {
        BoundedParameterValidator<Integer> validator = BoundedParameterValidator.integerBuilder().withUpperBound(Bound.inclusive(0)).build();
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", new ModelNode(-1)));
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", ModelNode.ZERO));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode(1)));
    }

    @Test
    public void bounded() {
        BoundedParameterValidator<Integer> validator = BoundedParameterValidator.integerBuilder().withLowerBound(Bound.inclusive(0)).withUpperBound(Bound.exclusive(10)).build();
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode(-1)));
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", ModelNode.ZERO));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode(10)));
    }

    @Test
    public void restrictive() {
        BoundedParameterValidator<Integer> validator = BoundedParameterValidator.integerBuilder().withLowerBound(Bound.inclusive(0)).withUpperBound(Bound.inclusive(0)).build();
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode(-1)));
        assertThatNoException().isThrownBy(() -> validator.validateParameter("valid", ModelNode.ZERO));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode(1)));
    }
}
