/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server.deployment;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link List} meant for use with an {@link Attachable} object.  The list is thread safe and performs
 * {@link Collections#checkedList(List, Class) run time type checking of all insertions}. Iterators do not
 * support modifications.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class AttachmentList<T> implements List<T>, RandomAccess {

    private final Class<T> valueClass;
    private final List<T> delegate;

    /**
     * Creates a new {@code AttachmentList}.
     *
     * @param initialCapacity ignored
     * @param valueClass the type of element the list is permitted to hold
     * @deprecated use {@code new AttachmentList<>(valueClass)}
     */
    @SuppressWarnings("unused")
    @Deprecated
    public AttachmentList(final int initialCapacity, final Class<T> valueClass) {
        // Ignore the initial capacity. CopyOnWriteArrayList is going to create a new array for any
        // update so there is no point creating an initial array of any size other than zero.
        this(valueClass);
    }

    /**
     * Creates a new {@code AttachmentList}.
     *
     * @param valueClass the type of element the list is permitted to hold
     */
    public AttachmentList(final Class<T> valueClass) {
        delegate = Collections.checkedList(new CopyOnWriteArrayList<>(), valueClass);
        this.valueClass = valueClass;
    }

    /**
     * Creates a new {@code AttachmentList}.
     *
     * @param c initial contents of the list. Cannot be {@code null}
     * @param valueClass the type of element the list is permitted to hold
     */
    public AttachmentList(final Collection<? extends T> c, final Class<T> valueClass) {
        delegate = Collections.checkedList(new CopyOnWriteArrayList<T>(c), valueClass);
        this.valueClass = valueClass;
    }
    
    public Class<T> getValueClass() {
        return valueClass;
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public boolean contains(final Object o) {
        return delegate.contains(o);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Modifications (i.e. {@code remove} calls) throw {@link UnsupportedOperationException}.
     */
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    public Object[] toArray() {
        return delegate.toArray();
    }

    public <T> T[] toArray(final T[] a) {
        return delegate.toArray(a);
    }

    public boolean add(final T t) {
        return delegate.add(t);
    }

    public boolean remove(final Object o) {
        return delegate.remove(o);
    }

    public boolean containsAll(final Collection<?> c) {
        return delegate.containsAll(c);
    }

    public boolean addAll(final Collection<? extends T> c) {
        return delegate.addAll(c);
    }

    public boolean addAll(final int index, final Collection<? extends T> c) {
        return delegate.addAll(index, c);
    }

    public boolean removeAll(final Collection<?> c) {
        return delegate.removeAll(c);
    }

    public boolean retainAll(final Collection<?> c) {
        return delegate.retainAll(c);
    }

    public void clear() {
        delegate.clear();
    }

    public boolean equals(final Object o) {
        return delegate.equals(o);
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public T get(final int index) {
        return delegate.get(index);
    }

    public T set(final int index, final T element) {
        return delegate.set(index, element);
    }

    public void add(final int index, final T element) {
        delegate.add(index, element);
    }

    public T remove(final int index) {
        return delegate.remove(index);
    }

    public int indexOf(final Object o) {
        return delegate.indexOf(o);
    }

    public int lastIndexOf(final Object o) {
        return delegate.lastIndexOf(o);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Modifications (i.e. {@code remove}, @{code set} and {@code add} calls) throw {@link UnsupportedOperationException}.
     */
    public ListIterator<T> listIterator() {
        return delegate.listIterator();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Modifications (i.e. {@code remove}, @{code set} and {@code add} calls) throw {@link UnsupportedOperationException}.
     */
    public ListIterator<T> listIterator(final int index) {
        return delegate.listIterator(index);
    }

    public List<T> subList(final int fromIndex, final int toIndex) {
        return delegate.subList(fromIndex, toIndex);
    }
}
