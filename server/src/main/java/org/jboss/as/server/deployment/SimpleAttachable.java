/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.wildfly.common.Assert;

/**
 * A simple implementation of {@link Attachable} which may be used as a base class or on a standalone basis.
 * <p>
 * This class is thread safe, as all methods are synchronized.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class SimpleAttachable implements Attachable {
    private final Map<AttachmentKey<?>, Object> attachments = new HashMap<AttachmentKey<?>, Object>();

    /** {@inheritDoc} */
    public synchronized boolean hasAttachment(AttachmentKey<?> key) {
        if (key == null) {
            return false;
        }
        return attachments.containsKey(key);
    }

    /** {@inheritDoc} */
    public synchronized <T> T getAttachment(final AttachmentKey<T> key) {
        if (key == null) {
            return null;
        }
        return key.cast(attachments.get(key));
    }

    /** {@inheritDoc} */
    public synchronized <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key) {
        if (key == null) {
            return null;
        }
        List<T> list = key.cast(attachments.get(key));
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    /** {@inheritDoc} */
    public synchronized <T> T putAttachment(final AttachmentKey<T> key, final T value) {
        Assert.checkNotNullParam("key", key);
        return key.cast(attachments.put(key, key.cast(value)));
    }

    /** {@inheritDoc} */
    public synchronized <T> T removeAttachment(final AttachmentKey<T> key) {
        if (key == null) {
            return null;
        }
        return key.cast(attachments.remove(key));
    }

    /** {@inheritDoc} */
    public synchronized <T> void addToAttachmentList(final AttachmentKey<AttachmentList<T>> key, final T value) {
        if (key != null) {
            final Map<AttachmentKey<?>, Object> attachments = this.attachments;
            final AttachmentList<T> list = key.cast(attachments.get(key));
            if (list == null) {
                final AttachmentList<T> newList = new AttachmentList<T>(((ListAttachmentKey<T>) key).getValueClass());
                attachments.put(key, newList);
                newList.add(value);
            } else {
                list.add(value);
            }
        }
    }

    public synchronized Set<AttachmentKey<?>> attachmentKeys() {
        return new HashSet<AttachmentKey<?>>(attachments.keySet());
    }
}
