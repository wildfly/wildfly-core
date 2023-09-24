/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.transform;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.as.controller.OperationContext;
import org.wildfly.common.Assert;

/**
 * Internal class for providing attachments. Although it is in the .transform package, this is more to avoid polluting
 * the parent package.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ContextAttachments implements AutoCloseable {
    /**
     * A concurrent map for the attachments.
     */
    private final ConcurrentMap<OperationContext.AttachmentKey<?>, Object> valueAttachments = new ConcurrentHashMap<>();

    public ContextAttachments() {
    }

    public <V> V getAttachment(final OperationContext.AttachmentKey<V> key) {
        Assert.checkNotNullParam("key", key);
        return key.cast(valueAttachments.get(key));
    }

    public <V> V attach(final OperationContext.AttachmentKey<V> key, final V value) {
        Assert.checkNotNullParam("key", key);
        return key.cast(valueAttachments.put(key, value));
    }

    public <V> V attachIfAbsent(final OperationContext.AttachmentKey<V> key, final V value) {
        Assert.checkNotNullParam("key", key);
        return key.cast(valueAttachments.putIfAbsent(key, value));
    }

    public <V> V detach(final OperationContext.AttachmentKey<V> key) {
        Assert.checkNotNullParam("key", key);
        return key.cast(valueAttachments.remove(key));
    }

    @Override
    public void close() {
        valueAttachments.clear();
    }
}