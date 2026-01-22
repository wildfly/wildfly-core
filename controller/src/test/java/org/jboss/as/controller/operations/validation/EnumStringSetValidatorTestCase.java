/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import static org.assertj.core.api.Assertions.*;

import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Unit test for {@link EnumStringSetValidator}.
 * @author Paul Ferraro
 */
public class EnumStringSetValidatorTestCase {

    @Test
    public void allOf() {
        this.verify(new EnumStringSetValidator<>(ChronoUnit.class), EnumSet.allOf(ChronoUnit.class));
    }

    @Test
    public void of() {
        Set<ChronoUnit> allowed = EnumSet.of(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS);
        this.verify(new EnumStringSetValidator<>(allowed), allowed);
    }

    private void verify(ParameterValidator validator, Set<ChronoUnit> allowed) {
        for (ChronoUnit unit : EnumSet.allOf(ChronoUnit.class)) {
            ThrowingCallable validation = () -> validator.validateParameter("unit", new ModelNode(unit.toString()));
            if (allowed.contains(unit)) {
                assertThatNoException().isThrownBy(validation);
            } else {
                assertThatExceptionOfType(OperationFailedException.class).isThrownBy(validation);
            }
            assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("unit", new ModelNode(unit.name())));
        }
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", new ModelNode("foo")));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", ModelNode.TRUE));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> validator.validateParameter("invalid", ModelNode.ZERO));
    }
}
