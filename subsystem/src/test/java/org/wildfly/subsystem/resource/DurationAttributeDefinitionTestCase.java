/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.Bound;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Unit test for {@link DurationAttributeDefinition}.
 * @author Paul Ferraro
 */
public class DurationAttributeDefinitionTestCase {

    @Test
    public void illegalBounds() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").withUpperBound(Bound.exclusive(Duration.ZERO)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").withUpperBound(Bound.inclusive(Duration.ZERO)).withLowerBound(Bound.exclusive(Duration.ZERO)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").withLowerBound(Bound.exclusive(Duration.ZERO)).withUpperBound(Bound.inclusive(Duration.ZERO)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").withUpperBound(Bound.exclusive(Duration.ofSeconds(1))).setDefaultValue(Duration.ofSeconds(1)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").withLowerBound(Bound.inclusive(Duration.ofMillis(1))).withUpperBound(Bound.exclusive(Duration.ofSeconds(1))).setDefaultValue(Duration.ZERO));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").setDefaultValue(Duration.ZERO).withLowerBound(Bound.exclusive(Duration.ZERO)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").setDefaultValue(Duration.ofSeconds(1)).withUpperBound(Bound.exclusive(Duration.ofSeconds(1))));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").setDefaultValue(Duration.ofSeconds(1)).setAllowedValues(Set.of(Duration.ZERO)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DurationAttributeDefinition.builder("foo").setAllowedValues(Set.of(Duration.ZERO)).setDefaultValue(Duration.ofSeconds(1)));
    }

    @Test
    public void requiredISO8601() throws OperationFailedException {
        String name = "foo";
        DurationAttributeDefinition attribute = DurationAttributeDefinition.builder(name).build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isTrue();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Validation should fail, parameter is not defined
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(ModelNode.ZERO_LONG);

        // Validation should fail, parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(new ModelNode(Duration.ofSeconds(-1L).toString()));

        // Validation should fail, parameter defines negative duration
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(new ModelNode(Duration.ZERO.toString()));

        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isZero();

        Duration duration = Duration.ofSeconds(1L);
        operation.get(name).set(new ModelNode(duration.toString()));

        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isEqualTo(duration);
    }

    @Test
    public void optionalISO8601() throws OperationFailedException {
        String name = "foo";
        DurationAttributeDefinition attribute = DurationAttributeDefinition.builder(name).setRequired(false).withLowerBound(Bound.exclusive(Duration.ZERO)).build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        attribute.validateAndSet(operation, model);
        // Optional w/out default should resolve to null
        assertThat(attribute.resolve(context, model)).isNull();

        operation.get(name).set(ModelNode.ZERO_LONG);

        // Validation should fail, parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(new ModelNode(Duration.ZERO.toString()));

        // Validation should fail, since lower bound is now exclusive
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        Duration duration = Duration.ofMillis(1);
        operation.get(name).set(new ModelNode(duration.toString()));

        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isEqualTo(duration);
    }

    @Test
    public void defaultValueISO8601() throws OperationFailedException {
        String name = "foo";
        Duration defaultDuration = Duration.ofSeconds(1);
        DurationAttributeDefinition attribute = DurationAttributeDefinition.builder(name).setDefaultValue(defaultDuration).withUpperBound(Bound.exclusive(Duration.ofSeconds(10))).build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();
        assertThat(attribute.getDefaultValue()).isEqualTo(new ModelNode(defaultDuration.toString()));

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Should resolve to default value
        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isEqualTo(defaultDuration);

        operation.get(name).set(ModelNode.ZERO_LONG);

        // Validation should fail, parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(new ModelNode(Duration.ZERO.toString()));

        // Validation should succeed since we are without bounds
        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isZero();

        Duration duration = Duration.ofSeconds(10L);
        operation.get(name).set(new ModelNode(duration.toString()));

        // Validation should fail since we exceeded upper bound
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));
    }

    @Test
    public void requiredUnit() throws OperationFailedException {
        String name = "foo";
        DurationAttributeDefinition attribute = DurationAttributeDefinition.builder(name, ChronoUnit.MILLIS).build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isTrue();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Validation should fail, parameter is not defined
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(Duration.ZERO.toString());

        // Validation should fail, parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(new ModelNode(Duration.ofSeconds(-1L).toMillis()));

        // Validation should fail, parameter defines negative duration
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(ModelNode.ZERO_LONG);

        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isZero();

        Duration duration = Duration.ofSeconds(1L);
        operation.get(name).set(new ModelNode(duration.toMillis()));

        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isEqualTo(duration);
    }

    @Test
    public void optionalUnit() throws OperationFailedException {
        String name = "foo";
        DurationAttributeDefinition attribute = DurationAttributeDefinition.builder(name, ChronoUnit.MILLIS).setRequired(false).withLowerBound(Bound.exclusive(Duration.ZERO)).build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        attribute.validateAndSet(operation, model);
        // Optional w/out default should resolve to null
        assertThat(attribute.resolve(context, model)).isNull();

        operation.get(name).set(Duration.ZERO.toString());

        // Validation should fail, parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(ModelNode.ZERO_LONG);

        // Validation should fail, since lower bound is now exclusive
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        Duration duration = Duration.ofMillis(1);
        operation.get(name).set(new ModelNode(duration.toMillis()));

        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isEqualTo(duration);
    }

    @Test
    public void defaultValueUnit() throws OperationFailedException {
        String name = "foo";
        Duration defaultDuration = Duration.ofSeconds(1);
        DurationAttributeDefinition attribute = DurationAttributeDefinition.builder(name, ChronoUnit.MILLIS).setDefaultValue(defaultDuration).withUpperBound(Bound.exclusive(Duration.ofSeconds(10))).build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();
        assertThat(attribute.getDefaultValue()).isEqualTo(new ModelNode(defaultDuration.toMillis()));

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Should resolve to default value
        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isEqualTo(defaultDuration);

        operation.get(name).set(Duration.ZERO.toString());

        // Validation should fail, parameter is not resolvable
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));

        operation.get(name).set(ModelNode.ZERO_LONG);

        // Validation should succeed since we are without bounds
        attribute.validateAndSet(operation, model);
        assertThat(attribute.resolve(context, model)).isZero();

        Duration duration = Duration.ofSeconds(10L);
        operation.get(name).set(new ModelNode(duration.toMillis()));

        // Validation should fail since we exceeded upper bound
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateOperation(operation));
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(() -> attribute.validateAndSet(operation, model));
    }
}
