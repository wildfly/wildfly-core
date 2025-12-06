/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A bound of a parameter value.
 * @param <T> the parameter value type
 */
public interface Bound<T> extends Supplier<T> {
    /**
     * Indicates whether or not this bound is exclusive.
     * @return true, if this bound is exclusive, false otherwise.
     */
    boolean isExclusive();

    /**
     * Indicates whether or not this bound is inclusive.
     * @return true, if this bound is inclusive, false otherwise.
     */
    default boolean isInclusive() {
        return !this.isExclusive();
    }

    /**
     * Creates an inclusive bound.
     * @param <T> the bound value type
     * @param value the bound value
     * @return an inclusive bound.
     */
    static <T> Bound<T> inclusive(T value) {
        return new AbstractBound<>(value) {
            @Override
            public boolean isExclusive() {
                return false;
            }
        };
    }

    /**
     * Creates an inclusive bound.
     * @param <T> the bound value type
     * @param value the bound value
     * @return an inclusive bound.
     */
    static <T> Bound<T> exclusive(T value) {
        return new AbstractBound<>(value) {
            @Override
            public boolean isExclusive() {
                return true;
            }
        };
    }

    abstract class AbstractBound<T> implements Bound<T> {
        private T value;

        AbstractBound(T value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public T get() {
            return this.value;
        }

        @Override
        public int hashCode() {
            return this.value.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            return (object instanceof Bound bound) ? (this.get().equals(bound.get()) && this.isExclusive() == bound.isExclusive()) : false;
        }

        @Override
        public String toString() {
            return String.format("%s, %s", this.value, this.isExclusive() ? "exclusive" : "inclusive");
        }
    }
}