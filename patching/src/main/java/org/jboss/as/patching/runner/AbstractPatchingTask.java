/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.IoUtils.NO_CONTENT;

import java.io.IOException;
import java.util.Arrays;

import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.ModificationCondition;

/**
 * Basic patching task implementation.
 *
 * @author Emanuel Muckenhuber
 */
abstract class AbstractPatchingTask<T extends ContentItem> implements PatchingTask {

    protected final T contentItem;
    protected final PatchingTaskDescription description;

    private boolean ignoreApply;   // completely ignore the apply step
    private boolean skipExecution; // Skip the execution step
    protected byte[] backupHash = NO_CONTENT; // The backup hash

    AbstractPatchingTask(PatchingTaskDescription description, Class<T> expected) {
        this.description = description;
        this.contentItem = description.getContentItem(expected);
    }

    @Override
    public T getContentItem() {
        return contentItem;
    }

    @Override
    public boolean isRelevant(PatchingTaskContext context) throws PatchingException {
        final ModificationCondition condition = description.getModification().getCondition();
        if(condition == null) {
            return true;
        }
        return condition.isSatisfied(context);
    }

    /**
     * Backup the content.
     *
     * @param context the patching context
     * @return the hash of the content
     * @throws IOException
     */
    abstract byte[] backup(PatchingTaskContext context) throws IOException;

    /**
     * Apply the modification.
     *
     * @param context the patching context
     * @param loader the patch content loader
     * @return the actual copied content hash
     * @throws IOException
     */
    abstract byte[] apply(final PatchingTaskContext context, final PatchContentLoader loader) throws IOException;

    /**
     * Create the rollback entry.
     *
     * @param original the original content modification
     * @param targetHash the new target hash code (current content)
     * @param itemHash the new content item hash (backup content)
     * @return the rollback modification information
     */
    abstract ContentModification createRollbackEntry(ContentModification original, byte[] targetHash, byte[] itemHash);

    /**
     * Fail if the copied content is different from the one specified in the metadata. This should be true in most of the
     * cases. Only removing a module does not really match this, since we are creating a removed-module rather than
     * removing the contents.
     *
     * @param context the task context
     * @return
     */
    protected boolean failOnContentMismatch(PatchingTaskContext context) {
        return context.getCurrentMode() != PatchingTaskContext.Mode.UNDO;
    }

    /**
     * Get the original modification. Tasks like module remove can override this and fix the hashes based on the created content.
     *
     * @param targetHash the new target hash code (current content)
     * @param itemHash the new content item hash (backup content)
     * @return the original modification
     */
    protected ContentModification getOriginalModification(byte[] targetHash, byte[] itemHash) {
        return description.getModification();
    }

    /**
     * Completely skip the apply step.
     */
    protected void setIgnoreApply() {
        ignoreApply = true;
    }

    @Override
    public boolean prepare(final PatchingTaskContext context) throws IOException {
        // Backup
        backupHash = backup(context);
        // If the content is already present just resolve any conflict automatically
        final byte[] contentHash = contentItem.getContentHash();
        if(Arrays.equals(backupHash, contentHash)) {
            // Skip execute for misc items only
            skipExecution = contentItem.getContentType() == ContentType.MISC && backupHash != NO_CONTENT;
            return true;
        }
        // See if the content matches our expected target
        final byte[] expected = description.getModification().getTargetHash();
        if(Arrays.equals(backupHash, expected)) {
            // Don't resolve conflicts from the history
            return ! description.hasConflicts();
        }
        return false;
    }

    @Override
    public void execute(final PatchingTaskContext context) throws IOException {
        if (ignoreApply) {
            return;
        }
        final PatchContentLoader contentLoader = description.getLoader();
        final boolean skip = skipExecution || context.isExcluded(contentItem);
        final byte[] contentHash;
        if(skip) {
            contentHash = backupHash; // Reuse the backup hash
        } else {
            contentHash = apply(context, contentLoader); // Copy the content
        }
        // Add the rollback action
        final ContentModification original = getOriginalModification(contentHash, backupHash);
        final ContentModification rollbackAction = createRollbackEntry(original, contentHash, backupHash);
        context.recordChange(original, rollbackAction);
        // Fail after adding the undo action
        if (! Arrays.equals(contentHash, contentItem.getContentHash()) && failOnContentMismatch(context) && !context.isIgnored(contentItem)) {
            throw PatchLogger.ROOT_LOGGER.wrongCopiedContent(contentItem);
        }
    }

}
