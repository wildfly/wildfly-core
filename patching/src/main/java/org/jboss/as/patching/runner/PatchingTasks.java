/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.runner.IdentityPatchContext.PatchEntry;

/**
 * Utility class to auto resolve conflicts when rolling back/invalidating patches.
 *
 * The base for determining whether a release or cumulative patch can be applied cleanly (no conflicts) is when all one-patches
 * have been rolled back. For a rollback we go through the recorded history and update the target (current state), so
 * that we are left with a single transition. Additionally we track whether conflicts had to be resolved when applying
 * a previous patch and mark this as a conflict to be resolved when applying this patch.
 *
 * @author Emanuel Muckenhuber
 */
class PatchingTasks {

    static final EnumSet<ModificationType> ALL_MODIFICATIONS = EnumSet.allOf(ModificationType.class);
    static final EnumSet<ModificationType> ALL_BUT_MODIFY = EnumSet.of(ModificationType.ADD, ModificationType.REMOVE);

    /**
     * Process multiple patches for rollback, trying to determine the current and target state for this applying this combination.
     * <p/>
     * This will track changes by location, trying to merge multiple changes per location and recording whether conflicts
     * were detected or not. It's up to the user to skip the content validation steps.
     * </p>
     * Compare the recorded rollback history and the original patch - to detect conflicts.
     *
     * @param patchId       the patch id
     * @param originalPatch the original modifications
     * @param rollbackPatch the rollback modifications
     * @param modifications the definitions
     * @param filter        the content filter
     * @param mode          the current patching mode
     */
    static void rollback(final String patchId, final Collection<ContentModification> originalPatch, final Collection<ContentModification> rollbackPatch,
                         final ContentTaskDefinitions modifications, final ContentItemFilter filter,
                         final PatchingTaskContext.Mode mode) {

        // Process the original patch information
        final Map<Location, ContentModification> originalModifications = new HashMap<Location, ContentModification>(originalPatch.size());
        for (final ContentModification modification : originalPatch) {
            originalModifications.put(new Location(modification.getItem()), modification);
        }
        // Process the rollback information
        for (final ContentModification modification : rollbackPatch) {

            final ContentItem item = modification.getItem();
            // Skip module items when rolling back
            if (!filter.accepts(item)) {
                continue;
            }
            final Location location = new Location(item);
            final ContentModification original = originalModifications.remove(location);
            final ContentEntry contentEntry = new ContentEntry(patchId, modification);
            ContentTaskDefinition definition = modifications.get(location);
            if (definition == null) {
                definition = new ContentTaskDefinition(location, contentEntry, true);
                modifications.put(location, definition);
            } else {
                // TODO perhaps we don't need check that
                boolean strict = true; // Strict history checks
                if (strict) {
                    // Check if the consistency of the history
                    final ContentEntry previous = definition.getTarget();
                    final byte[] hash = previous.getItem().getContentHash();
                    if (!Arrays.equals(hash, contentEntry.getTargetHash())) {
                        throw new IllegalStateException();
                    }
                }
                //
                definition.setTarget(contentEntry);
            }
            if (original == null
                    || mode == PatchingTaskContext.Mode.ROLLBACK) {
                continue;
            }

            // Check if the current content was the original item (preserve)
            final byte[] currentContent = modification.getTargetHash();
            final byte[] originalContent = original.getItem().getContentHash();

            // TODO relax requirements for conflict resolution on rollback!
            if (!Arrays.equals(currentContent, originalContent)) {
                definition.addConflict(contentEntry);
            } else {
                // Check if backup item was the targeted one (override)
                final byte[] backupItem = item.getContentHash();
                final byte[] originalTarget = original.getTargetHash();
                //
                if (!Arrays.equals(backupItem, originalTarget)) {
                    definition.addConflict(contentEntry);
                }
            }
        }
    }

    static void addMissingModifications(IdentityPatchContext.PatchEntry target, Collection<ContentModification> modifications, final ContentItemFilter filter) throws IOException {
        final String cpId = target.getCumulativePatchID();
        for (final ContentModification modification : modifications) {

            final ContentItem item = modification.getItem();
            // Check if we accept the item
            if (!filter.accepts(item)) {
                continue;
            }

            final Location location = new Location(item);
            final ContentTaskDefinition definition = target.get(location);
            if (definition == null) {
                target.put(location, new ContentTaskDefinition(location, new ContentEntry(cpId, modification), false));
            } else if(definition.isRollback()) {
                target.prepareForPortForward(item, cpId);
                definition.setTarget(new ContentEntry(cpId, modification));
            }
        }
    }

    static void apply(final String patchId, final Collection<ContentModification> modifications, final PatchEntry patchEntry) {
        apply(patchId, modifications, patchEntry, ContentItemFilter.ALL);
    }

    /**
     * Apply modifications to a content task definition.
     *
     * @param patchId       the patch id
     * @param modifications the modifications
     * @param definitions   the task definitions
     * @param filter        the content item filter
     */
    static void apply(final String patchId, final Collection<ContentModification> modifications, final PatchEntry patchEntry, final ContentItemFilter filter) {
        for (final ContentModification modification : modifications) {

            final ContentItem item = modification.getItem();
            // Check if we accept the item
            if (!filter.accepts(item)) {
                continue;
            }

            final Location location = new Location(item);
            final ContentEntry contentEntry = new ContentEntry(patchId, modification);
            ContentTaskDefinition definition = patchEntry.get(location);
            if (definition == null) {
                definition = new ContentTaskDefinition(location, contentEntry, false);
                patchEntry.put(location, definition);
            } else {
                definition.setTarget(contentEntry);
            }
        }
    }

    static class ContentTaskDefinition {

        private final Location location;
        private final ContentEntry latest;
        private ContentEntry target;
        private boolean rollback;
        private final List<ContentEntry> conflicts = new ArrayList<ContentEntry>();

        ContentTaskDefinition(Location location, ContentEntry latest, boolean rollback) {
            this.location = location;
            this.latest = latest;
            this.target = latest;
            this.rollback = rollback;
        }

        public boolean isRollback() {
            return rollback;
        }

        public Location getLocation() {
            return location;
        }

        public ContentEntry getLatest() {
            return latest;
        }

        public ContentEntry getTarget() {
            return target;
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }

        public List<ContentEntry> getConflicts() {
            return conflicts;
        }

        void setTarget(final ContentEntry entry) {
            target = entry;
            rollback = false;
        }

        void addConflict(ContentEntry entry) {
            conflicts.add(entry);
        }

    }

    static class ContentEntry {

        final String patchId;
        final ContentModification modification;

        ContentEntry(String patchId, ContentModification modification) {
            this.patchId = patchId;
            this.modification = modification;
        }

        public String getPatchId() {
            return patchId;
        }

        public ContentModification getModification() {
            return modification;
        }

        public ContentItem getItem() {
            return modification.getItem();
        }

        public byte[] getTargetHash() {
            return modification.getTargetHash();
        }

    }

}
