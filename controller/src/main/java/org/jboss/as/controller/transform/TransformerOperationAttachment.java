/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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