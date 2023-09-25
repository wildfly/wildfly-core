/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import java.util.Arrays;

import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.ModificationType;

/**
 * @author Emanuel Muckenhuber
 */
class PatchingTaskDescription {

    private final String patchId;
    private final ContentModification modification;
    private final PatchContentLoader loader;

    private final boolean conflicts;
    private final boolean skipIfTheSame;
    private final boolean rollback;

    PatchingTaskDescription(String patchId, ContentModification modification, PatchContentLoader loader,
                            boolean conflicts, boolean skipIfExists, boolean rollback) {
        this.patchId = patchId;
        this.modification = modification;
        this.loader = loader;
        this.conflicts = conflicts;
        this.skipIfTheSame = skipIfExists;
        this.rollback = rollback;
    }

    public String getPatchId() {
        return patchId;
    }

    public boolean isRolledback() {
        return rollback;
    }

    public ContentModification getModification() {
        return modification;
    }

    public ModificationType getModificationType() {
        return modification.getType();
    }

    public ContentItem getContentItem() {
        return modification.getItem();
    }

    public <T extends ContentItem> T getContentItem(Class<T> expected) {
        return modification.getItem(expected);
    }

    public ContentType getContentType() {
        return modification.getItem().getContentType();
    }

    public PatchContentLoader getLoader() {
        return loader;
    }

    public boolean hasConflicts() {
        return conflicts;
    }

    public boolean skipIfTheSame() {
        return skipIfTheSame;
    }

    static PatchingTaskDescription create(final PatchingTasks.ContentTaskDefinition definition, final PatchContentLoader loader) {
        final ContentModification modification = resolveDefinition(definition);

        // Check if we already have the new content
        final ContentItem item = definition.getTarget().getItem();
        final byte[] currentHash = definition.getLatest().getTargetHash();
        final byte[] newContentHash = item.getContentHash();
        boolean skipIfExists = Arrays.equals(currentHash, newContentHash);

        return new PatchingTaskDescription(definition.getTarget().getPatchId(), modification, loader, definition.hasConflicts(), skipIfExists, definition.isRollback());

    }

    static ContentModification resolveDefinition(final PatchingTasks.ContentTaskDefinition definition) {
        // Only available in a single patch, yay!
        if(definition.getLatest() == definition.getTarget()) {
            return definition.getTarget().getModification();
        }

        // Create a new modification replacing the latest
        final ContentItem backupItem = definition.getTarget().getItem();
        final ContentModification modification = definition.getTarget().getModification();
        final byte[] target = definition.getLatest().getTargetHash();
        return new ContentModification(backupItem, target, modification.getType(), modification.getCondition());
    }

}
