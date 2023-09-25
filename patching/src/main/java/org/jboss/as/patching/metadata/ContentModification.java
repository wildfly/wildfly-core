/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

/**
 * A modification of a content item. The {@linkplain ModificationType} describes whether the content
 * is added, modified or removed.
 *
 * @author Emanuel Muckenhuber
 */
public class ContentModification {

    private final ContentItem item;
    private final byte[] targetHash;
    private final ModificationType type;
    private final ModificationCondition condition;

    public ContentModification(ContentItem item, byte[] targetHash, ModificationType type, ModificationCondition condition) {
        this.item = item;
        this.targetHash = targetHash;
        this.type = type;
        this.condition = condition;
    }

    public ContentModification(ContentItem item, byte[] targetHash, ModificationType type) {
        this(item, targetHash, type, null);
    }

    public ContentModification(ContentItem item, ContentModification existing) {
        this(item, existing.getTargetHash(), existing.getType());
    }

    public ContentItem getItem() {
        return item;
    }

    public <T extends ContentItem> T getItem(Class<T> expected) {
        return expected.cast(item);
    }

    public byte[] getTargetHash() {
        return targetHash;
    }

    public ModificationType getType() {
        return type;
    }

    /**
     * Condition which has to be satisfied for this modification to be applied
     * to the target installation. If the condition is not satisfied,
     * the modification will be skipped and the patch application process will
     * proceed applying the remaining modifications in the patch.
     *
     * @return  modification condition or null if the modification does not depend
     * on any condition
     */
    public ModificationCondition getCondition() {
        return condition;
    }
}
