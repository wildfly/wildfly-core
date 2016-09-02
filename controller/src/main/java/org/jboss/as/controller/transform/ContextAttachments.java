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
public class ContextAttachments {
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
}