/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Unit test for {@link EnumAttributeDefinition}.
 * @author Paul Ferraro
 */
public class EnumAttributeDefinitionTestCase {

    @Test
    public void illegal() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> EnumAttributeDefinition.nameBuilder("foo", ChronoUnit.class).setAllowedValues(EnumSet.of(ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)).setDefaultValue(ChronoUnit.MILLIS));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> EnumAttributeDefinition.nameBuilder("foo", ChronoUnit.class).setDefaultValue(ChronoUnit.MILLIS).setAllowedValues(EnumSet.of(ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)));
    }

    @Test
    public void requiredName() throws OperationFailedException {
        String name = "unit";
        EnumAttributeDefinition<ChronoUnit> attribute = EnumAttributeDefinition.nameBuilder(name, ChronoUnit.class).build();
        assertThat(attribute.getName()).isSameAs(name);
        assertThat(attribute.getXmlName()).isSameAs(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isTrue();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Validation should fail since parameter is required
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        this.verifyName(attribute, EnumSet.allOf(ChronoUnit.class));
    }

    @Test
    public void optionalName() throws OperationFailedException {
        String name = "unit";
        Set<ChronoUnit> allowed = EnumSet.of(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS);
        EnumAttributeDefinition<ChronoUnit> attribute = EnumAttributeDefinition.nameBuilder(name, ChronoUnit.class).setRequired(false).setAllowedValues(allowed).build();
        assertThat(attribute.getName()).isSameAs(name);
        assertThat(attribute.getXmlName()).isSameAs(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Not required
        assertThatNoException().isThrownBy(() -> attribute.validateOperation(operation));
        assertThatNoException().isThrownBy(() -> attribute.validateAndSet(operation, model));
        // Undefined value should resolve to null
        assertThat(attribute.resolve(context, model)).isNull();

        this.verifyName(attribute, allowed);
    }

    @Test
    public void defaultName() throws OperationFailedException {
        String name = "unit";
        Set<ChronoUnit> allowed = EnumSet.of(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS);
        EnumAttributeDefinition<ChronoUnit> attribute = EnumAttributeDefinition.nameBuilder(name, ChronoUnit.class).setDefaultValue(ChronoUnit.SECONDS).setAllowedValues(allowed).build();
        assertThat(attribute.getName()).isSameAs(name);
        assertThat(attribute.getXmlName()).isSameAs(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Not required
        assertThatNoException().isThrownBy(() -> attribute.validateOperation(operation));
        assertThatNoException().isThrownBy(() -> attribute.validateAndSet(operation, model));
        // Should resolve to default value
        assertThat(attribute.resolve(context, model)).isEqualTo(ChronoUnit.SECONDS);

        this.verifyName(attribute, allowed);
    }

    @Test
    public void requiredToString() throws OperationFailedException {
        String name = "unit";
        EnumAttributeDefinition<ChronoUnit> attribute = EnumAttributeDefinition.toStringBuilder(name, ChronoUnit.class).build();
        assertThat(attribute.getName()).isSameAs(name);
        assertThat(attribute.getXmlName()).isSameAs(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isTrue();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Validation should fail since parameter is required
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        this.verifyToString(attribute, EnumSet.allOf(ChronoUnit.class));
    }

    @Test
    public void optionalToString() throws OperationFailedException {
        String name = "unit";
        Set<ChronoUnit> allowed = EnumSet.of(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS);
        EnumAttributeDefinition<ChronoUnit> attribute = EnumAttributeDefinition.toStringBuilder(name, ChronoUnit.class).setRequired(false).setAllowedValues(allowed).build();
        assertThat(attribute.getName()).isSameAs(name);
        assertThat(attribute.getXmlName()).isSameAs(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Not required
        assertThatNoException().isThrownBy(() -> attribute.validateOperation(operation));
        assertThatNoException().isThrownBy(() -> attribute.validateAndSet(operation, model));
        // Undefined value should resolve to null
        assertThat(attribute.resolve(context, model)).isNull();

        this.verifyToString(attribute, allowed);
    }

    @Test
    public void defaultToString() throws OperationFailedException {
        String name = "unit";
        Set<ChronoUnit> allowed = EnumSet.of(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS);
        EnumAttributeDefinition<ChronoUnit> attribute = EnumAttributeDefinition.toStringBuilder(name, ChronoUnit.class).setDefaultValue(ChronoUnit.SECONDS).setAllowedValues(allowed).build();
        assertThat(attribute.getName()).isSameAs(name);
        assertThat(attribute.getXmlName()).isSameAs(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Not required
        assertThatNoException().isThrownBy(() -> attribute.validateOperation(operation));
        assertThatNoException().isThrownBy(() -> attribute.validateAndSet(operation, model));
        // Should resolve to default value
        assertThat(attribute.resolve(context, model)).isEqualTo(ChronoUnit.SECONDS);

        this.verifyToString(attribute, allowed);
    }

    private void verifyName(EnumAttributeDefinition<ChronoUnit> attribute, Set<ChronoUnit> allowed) throws OperationFailedException {
        this.verify(attribute, allowed, ChronoUnit::name, ChronoUnit::toString);
    }

    private void verifyToString(EnumAttributeDefinition<ChronoUnit> attribute, Set<ChronoUnit> allowed) throws OperationFailedException {
        this.verify(attribute, allowed, ChronoUnit::toString, ChronoUnit::name);
    }

    private void verify(EnumAttributeDefinition<ChronoUnit> attribute, Set<ChronoUnit> allowed, Function<ChronoUnit, String> validFormat, Function<ChronoUnit, String> invalidFormat) throws OperationFailedException {
        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        ThrowingCallable operationValidation = () -> attribute.validateOperation(operation);
        ThrowingCallable modelValidation = () -> attribute.validateAndSet(operation, model);

        for (ChronoUnit unit : EnumSet.allOf(ChronoUnit.class)) {
            operation.get(attribute.getName()).set(validFormat.apply(unit));

            if (allowed.contains(unit)) {
                assertThatNoException().isThrownBy(operationValidation);
                assertThatNoException().isThrownBy(modelValidation);
            } else {
                // Validation should fail if parameter is not allowed
                assertThatExceptionOfType(OperationFailedException.class).isThrownBy(operationValidation);
                assertThatExceptionOfType(OperationFailedException.class).isThrownBy(modelValidation);
            }

            operation.get(attribute.getName()).set(invalidFormat.apply(unit));

            // Validation should fail if parameter is not resolvable
            assertThatExceptionOfType(OperationFailedException.class).isThrownBy(operationValidation);
            assertThatExceptionOfType(OperationFailedException.class).isThrownBy(modelValidation);
        }

        operation.get(attribute.getName()).set(new ModelNode("foo"));

        // Validation should fail if parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(operationValidation);
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(modelValidation);

        operation.get(attribute.getName()).set(ModelNode.TRUE);

        // Validation should fail if parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(operationValidation);
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(modelValidation);

        operation.get(attribute.getName()).set(ModelNode.ZERO);

        // Validation should fail if parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(operationValidation);
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(modelValidation);
    }
}
