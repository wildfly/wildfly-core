/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * A {@link List} meant for use with an {@link Attachable} object. The list has concurrency semantics equivalent to
 * {@code Collections.synchronizedList}; i.e. it is thread safe for reads and writes not involving an iterator or stream
 * but if reads can occur concurrently with writes it is imperative that the user manually synchronize on the list
 * when iterating over it:
 * <pre>
 * AttachmentList<String></String> list = new AttachmentList<>()</>;
 * ...
 * synchronized (list) {
 *      Iterator i = list.iterator(); // Must be in synchronized block
 *      while (i.hasNext())
 *          foo(i.next());
 * }
 * </pre>
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class AttachmentList<E> implements List<E>, RandomAccess {

    private final Class<E> valueClass;
    private final List<E> delegate;
    private final Object mutex;

    /**
     * Creates a new {@code AttachmentList}.
     *
     * @param initialCapacity the initial capacity of the list
     * @param valueClass the type of element the list is permitted to hold
     */
    public AttachmentList(final int initialCapacity, final Class<E> valueClass) {
        delegate = Collections.checkedList(new ArrayList<>(initialCapacity), valueClass);
        this.valueClass = valueClass;
        mutex = this;
    }

    /**
     * Creates a new {@code AttachmentList}.
     *
     * @param valueClass the type of element the list is permitted to hold
     */
    public AttachmentList(final Class<E> valueClass) {
        this(10, valueClass); // Use default ArrayList initial capacity
    }

    /**
     * Creates a new {@code AttachmentList}.
     *
     * @param c initial contents of the list. Cannot be {@code null}
     * @param valueClass the type of element the list is permitted to hold
     */
    public AttachmentList(final Collection<? extends E> c, final Class<E> valueClass) {
        delegate = Collections.checkedList(new ArrayList<>(), valueClass);
        delegate.addAll(c);
        this.valueClass = valueClass;
        mutex = this;
    }

    /** For use by {@link #subList(int, int)} */
    private AttachmentList(final List<E> sublist, final Class<E> valueClass, Object mutex) {
        delegate = sublist;
        this.valueClass = valueClass;
        this.mutex = mutex;
    }

    @SuppressWarnings("WeakerAccess")
    public Class<E> getValueClass() {
        return valueClass;
    }

    @Override
    public int size() {
        synchronized (mutex) {
            return delegate.size();
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (mutex) {
            return delegate.isEmpty();
        }
    }

    @Override
    public boolean contains(final Object o) {
        synchronized (mutex) {
            return delegate.contains(o);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If reads can occur concurrently with writes it is imperative that the user manually synchronize on the list
     * when iterating over it.
     */
    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        synchronized (mutex) {
            return delegate.toArray();
        }
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        synchronized (mutex) {
            return delegate.toArray(a);
        }
    }

    @Override
    public boolean add(final E e) {
        synchronized (mutex) {
            return delegate.add(e);
        }
    }

    @Override
    public boolean remove(final Object o) {
        synchronized (mutex) {
            return delegate.remove(o);
        }
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        synchronized (mutex) {
            return delegate.containsAll(c);
        }
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        synchronized (mutex) {
            return delegate.addAll(c);
        }
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends E> c) {
        synchronized (mutex) {
            return delegate.addAll(index, c);
        }
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        synchronized (mutex) {
            return delegate.removeAll(c);
        }
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        synchronized (mutex) {
            return delegate.retainAll(c);
        }
    }

    @Override
    public void clear() {
        synchronized (mutex) {
            delegate.clear();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        synchronized (mutex) {
            return delegate.equals(o);
        }
    }

    @Override
    public int hashCode() {
        synchronized (mutex) {
            return delegate.hashCode();
        }
    }

    @Override
    public E get(final int index) {
        synchronized (mutex) {
            return delegate.get(index);
        }
    }

    @Override
    public E set(final int index, final E element) {
        synchronized (mutex) {
            return delegate.set(index, element);
        }
    }

    public void add(final int index, final E element) {
        synchronized (mutex) {
            delegate.add(index, element);
        }
    }

    @Override
    public E remove(final int index) {
        synchronized (mutex) {
            return delegate.remove(index);
        }
    }

    @Override
    public int indexOf(final Object o) {
        synchronized (mutex) {
            return delegate.indexOf(o);
        }
    }

    @Override
    public int lastIndexOf(final Object o) {
        synchronized (mutex) {
            return delegate.lastIndexOf(o);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>
     * If reads can occur concurrently with writes it is imperative that the user manually synchronize on the list
     * when iterating over it.
     */
    @Override
    public ListIterator<E> listIterator() {
        return delegate.listIterator();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>
     * If reads can occur concurrently with writes it is imperative that the user manually synchronize on the list
     * when iterating over it.
     */
    @Override
    public ListIterator<E> listIterator(final int index) {
        return delegate.listIterator(index);
    }

    @Override
    public List<E> subList(final int fromIndex, final int toIndex) {
        synchronized (mutex) {
            return new AttachmentList<>(delegate.subList(fromIndex, toIndex), valueClass, mutex);
        }
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        synchronized (mutex) {
            delegate.forEach(action);
        }
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        synchronized (mutex) {
            return delegate.removeIf(filter);
        }
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        synchronized (mutex) {
            delegate.replaceAll(operator);
        }
    }

    @Override
    public void sort(Comparator<? super E> c) {
        synchronized (mutex) {
            delegate.sort(c);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If reads can occur concurrently with writes it is imperative that the user manually synchronize on the list
     * when using the {@code Spliterator}.
     */
    @Override
    public Spliterator<E> spliterator() {
        return delegate.spliterator();
    }

    /**
     * {@inheritDoc}
     * <p>
     * If reads can occur concurrently with writes it is imperative that the user manually synchronize on the list
     * when using the {@code Stream}.
     */
    @Override
    public Stream<E> stream() {
        return delegate.stream();
    }

    /**
     * {@inheritDoc}
     * <p>
     * If reads can occur concurrently with writes it is imperative that the user manually synchronize on the list
     * when using the {@code Stream}.
     */
    @Override
    public Stream<E> parallelStream() {
        return delegate.parallelStream();
    }
}
