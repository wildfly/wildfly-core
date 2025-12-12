/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.Bound;
import org.jboss.as.controller.operations.validation.Bounded;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * An attribute definition that resolves to a {@link Duration}.
 * @author Paul Ferraro
 */
public class DurationAttributeDefinition  extends BoundedAttributeDefinition<Duration> {
    private static final Function<ModelNode, Duration> ISO_8601_RESOLVER = UnaryOperator.<ModelNode>identity().andThen(ModelNode::asString).andThen(Duration::parse);
    private static final Function<Duration, ModelNode> ISO_8601_FORMATTER = UnaryOperator.<Duration>identity().andThen(Duration::toString).andThen(ModelNode::new);

    DurationAttributeDefinition(Builder builder, Bounded<Duration> bounds) {
        super(builder, bounds);
    }

    /**
     * Creates a builder for a attribute definition expressed as an ISO 8601 time interval.
     * Requires duration to be non-negative by default.
     * @param attributeName the attribute name
     * @return a builder for a attribute definition expressed as an ISO 8601 time interval.
     */
    public static Builder builder(String attributeName) {
        return new Builder(attributeName, ModelType.STRING, ISO_8601_RESOLVER, ISO_8601_FORMATTER);
    }

    /**
     * Creates a builder for a attribute definition expressed as numeric value of the specified {@link ChronoUnit}.
     * Requires duration to be non-negative by default.
     * @param attributeName the attribute name
     * @param unit the unit of this attribute
     * @return a builder for a attribute definition expressed as numeric value of the specified {@link ChronoUnit}.
     * @throws IllegalArgumentException if the specified unit has no corresponding {@link MeasurementUnit}.
     */
    public static Builder builder(String attributeName, ChronoUnit unit) {
        return new Builder(attributeName, ModelType.LONG, value -> Duration.of(value.asLong(), unit), duration -> duration.isZero() ? ModelNode.ZERO_LONG : new ModelNode(unit.between(Instant.EPOCH, Instant.EPOCH.plus(duration))))
                .setMeasurementUnit(switch (unit) {
                    case NANOS -> MeasurementUnit.NANOSECONDS;
                    case MICROS -> MeasurementUnit.MICROSECONDS;
                    case MILLIS -> MeasurementUnit.MILLISECONDS;
                    case SECONDS -> MeasurementUnit.SECONDS;
                    case MINUTES -> MeasurementUnit.MINUTES;
                    case HOURS -> MeasurementUnit.HOURS;
                    case DAYS -> MeasurementUnit.DAYS;
                    default -> throw new IllegalArgumentException(unit.name());
                });
    }

    /**
     * Builds a duration-based attribute definition.
     * By default, this attribute allows expressions and may not define a negative duration.
     * Default bounds may be overridden via {@link #withLowerBound(Bound)} and/or {@link #withUpperBound(Bound)}.
     * Because validation and runtime resolution are tightly coupled, the following methods are unsupported.
     * <ul>
     * <li>{@link #setAllowedValues(int...)}</li>
     * <li>{@link #setAllowedValues(ModelNode...)}</li>
     * <li>{@link #setAllowedValues(String...)}</li>
     * <li>{@link #setDefaultValue(ModelNode)}</li>
     * <li>{@link #setValidator(ParameterValidator)}</li>
     * </ul>
     */
    public static class Builder extends BoundedAttributeDefinition.Builder<Duration, DurationAttributeDefinition, Builder> {

        Builder(String attributeName, ModelType type, Function<ModelNode, Duration> resolver, Function<Duration, ModelNode> formatter) {
            super(attributeName, type, resolver, formatter, Comparator.naturalOrder());
            // Require non-negative duration by default
            this.withLowerBound(Bound.inclusive(Duration.ZERO));
        }

        /**
         * Creates a new attribute definition builder based on the specified attribute definition.
         * @param basis the basis for this attribute definition builder
         */
        public Builder(DurationAttributeDefinition basis) {
            this(basis.getName(), basis);
        }

        /**
         * Creates a new attribute definition builder based on the specified attribute definition.
         * @param attributeName the name of this attribute
         * @param basis the basis for this attribute definition builder
         */
        public Builder(String attributeName, DurationAttributeDefinition basis) {
            super(attributeName, basis);
            this.withLowerBound(basis.getLowerBound()).withUpperBound(basis.getUpperBound());
        }

        @Override
        protected DurationAttributeDefinition build(Bounded<Duration> bounds) {
            return new DurationAttributeDefinition(this, bounds);
        }

        @Override
        protected Builder builder() {
            return this;
        }
    }
}
