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

import org.jboss.as.controller.OperationContext;

/**
 * If this attachment is attached to an OperationContext, its internal attachments get propagated to the transformers.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TransformerOperationAttachment {
    public static OperationContext.AttachmentKey<TransformerOperationAttachment> KEY =
            OperationContext.AttachmentKey.create(TransformerOperationAttachment.class);
    private final ContextAttachments contextAttachments = new ContextAttachments();

    public TransformerOperationAttachment() {
    }

    public static TransformerOperationAttachment getOrCreate(OperationContext context) {
        assert !context.isBooting() : "Only usable once booted";
        TransformerOperationAttachment attachment = new TransformerOperationAttachment();
        TransformerOperationAttachment original = context.attachIfAbsent(KEY, attachment);
        return original == null ? attachment : original;
    }


    public <V> V attach(final OperationContext.AttachmentKey<V> key, final V value) {
        return contextAttachments.attach(key, value);
    }

    public <V> V attachIfAbsent(final OperationContext.AttachmentKey<V> key, final V value) {
        return contextAttachments.attachIfAbsent(key, value);
    }

    public <V> V detach(final OperationContext.AttachmentKey<V> key) {
        return contextAttachments.detach(key);
    }

    public <V> V getAttachment(final OperationContext.AttachmentKey<V> key) {
        return contextAttachments.getAttachment(key);
    }
}