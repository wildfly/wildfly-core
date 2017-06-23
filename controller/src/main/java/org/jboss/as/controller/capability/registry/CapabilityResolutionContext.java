/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.capability.registry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.as.controller.registry.Resource;

/**
 * Contextual object that the {@code ModelController} and {@link CapabilityScope} implementations
 * can use to temporarily store data during the course of a capability resolution.
 *
 * @author Brian Stansberry
 */
public abstract class CapabilityResolutionContext {

    private final ConcurrentMap<AttachmentKey<?>, Object> contextAttachments = new ConcurrentHashMap<>();

    /**
     * Gets the root resource of the resource tree in effect during this resolution.
     * @return the root resource. Will not return {@code null}
     */
    public abstract Resource getResourceRoot();

    /**
     * Retrieves an object that has been attached to this context.
     *
     * @param key the key to the attachment.
     * @param <V> the value type of the attachment.
     *
     * @return the attachment if found otherwise {@code null}.
     */
    public <V> V getAttachment(final AttachmentKey<V> key) {
        assert key != null;
        return key.cast(contextAttachments.get(key));
    }

    /**
     * Attaches an arbitrary object to this context.
     *
     * @param key   they attachment key used to ensure uniqueness and used for retrieval of the value.
     * @param value the value to store.
     * @param <V>   the value type of the attachment.
     *
     * @return the previous value associated with the key or {@code null} if there was no previous value.
     */
    public <V> V attach(final AttachmentKey<V> key, final V value) {
        assert key != null;
        return key.cast(contextAttachments.put(key, value));
    }

    /**
     * Attaches an arbitrary object to this context only if the object was not already attached. If a value has already
     * been attached with the key provided, the current value associated with the key is returned.
     *
     * @param key   they attachment key used to ensure uniqueness and used for retrieval of the value.
     * @param value the value to store.
     * @param <V>   the value type of the attachment.
     *
     * @return the previous value associated with the key or {@code null} if there was no previous value.
     */
    public <V> V attachIfAbsent(final AttachmentKey<V> key, final V value) {
        assert key != null;
        return key.cast(contextAttachments.putIfAbsent(key, value));
    }

    /**
     * Detaches or removes the value from this context.
     *
     * @param key the key to the attachment.
     * @param <V> the value type of the attachment.
     *
     * @return the attachment if found otherwise {@code null}.
     */
    public <V> V detach(final AttachmentKey<V> key) {
        assert key != null;
        return key.cast(contextAttachments.remove(key));
    }

    /**
     * Resets this object, removing all attachments.
     */
    protected void reset() {
        contextAttachments.clear();
    }

    /**
     * Update this context content with the source context.
     *
     * @param source The context to copy.
     */
    protected void copy(CapabilityResolutionContext source) {
        reset();
        contextAttachments.putAll(source.contextAttachments);
    }

    /**
     * An attachment key instance.
     *
     * @param <T> the attachment value type
     */
    public static final class AttachmentKey<T> {

        private final Class<T> valueClass;

        /**
         * Construct a new instance.
         *
         * @param valueClass the value type.
         */
        private AttachmentKey(final Class<T> valueClass) {
            this.valueClass = valueClass;
        }

        /**
         * Cast the value to the type of this attachment key.
         *
         * @param value the value
         *
         * @return the cast value
         */
        public T cast(final Object value) {
            return valueClass.cast(value);
        }

        /**
         * Construct a new simple attachment key.
         *
         * @param valueClass the value class
         * @param <T>        the attachment type
         *
         * @return the new instance
         */
        @SuppressWarnings("unchecked")
        public static <T> AttachmentKey<T> create(final Class<? super T> valueClass) {
            return new AttachmentKey(valueClass);
        }
    }
}
