/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence.xml;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An {@link Iterable} decorator whose {@link Iterator} returns elements from mapping function without the need for an intermediate collection.
 * e.g.
 * <code>
 * Iterable<T> foo = ...;
 * Function<T, R> mapper = ...;
 * Iterable<R> bar = new MappedIterable<>(foo, mapper);
 * </code>
 * Effectively a light-weight equivalent to:
 * <code>
 * Iterable<R> bar = StreamSupport.stream(foo.spliterator(), false).map(mapper)::iterator;
 * </code>
 *
 * @author Paul Ferraro
 */
class MappedIterable<T, R> implements Iterable<R> {
    private final Iterable<T> iterable;
    private final Function<T, R> mapper;

    MappedIterable(Iterable<T> iterable, Function<T, R> mapper) {
        this.iterable = iterable;
        this.mapper = mapper;
    }

    @Override
    public Iterator<R> iterator() {
        return new MappedIterator<>(this.iterable.iterator(), this.mapper);
    }

    private static class MappedIterator<T, R> implements Iterator<R> {
        private final Iterator<T> iterator;
        private final Function<T, R> mapper;

        MappedIterator(Iterator<T> iterator, Function<T, R> mapper) {
            this.iterator = iterator;
            this.mapper = mapper;
        }

        @Override
        public boolean hasNext() {
            return this.iterator.hasNext();
        }

        @Override
        public R next() {
            return this.mapper.apply(this.iterator.next());
        }

        @Override
        public void remove() {
            this.iterator.remove();
        }

        @Override
        public void forEachRemaining(Consumer<? super R> action) {
            while (this.iterator.hasNext()) {
                action.accept(this.mapper.apply(this.iterator.next()));
            }
        }
    }
}
