/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import java.io.File;
import java.io.IOException;

import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.runner.PatchingTaskContext.Mode;

/**
 * A generic patching task.
 *
 * @author Emanuel Muckenhuber
 */
public interface PatchingTask {

    /**
     * Get the content item modified by this task.
     *
     * @return the content item
     */
    ContentItem getContentItem();

    /**
     * Checks whether this task is relevant in the given context or it can be skipped.
     *
     * @param context  patching task context
     * @return  true if this task is relevant in the current context and should be executed,
     *          otherwise - false
     * @throws PatchingException
     */
    boolean isRelevant(PatchingTaskContext context) throws PatchingException;

    /**
     * Prepare the content modification. This will backup the current target file and check
     * if the file was modified.
     *
     * @param  context the patching task context
     * @return whether it meets the modification tasks expectation
     * @throws IOException
     */
    boolean prepare(PatchingTaskContext context) throws IOException;

    /**
     * Execute.
     *
     * @param context the patching context
     * @throws IOException
     */
    void execute(final PatchingTaskContext context) throws IOException;

    static final class Factory {

        static PatchingTask create(final PatchingTaskDescription description, final IdentityPatchContext.PatchEntry context) {
            final ContentItem item = description.getContentItem();
            switch (item.getContentType()) {
                case BUNDLE:
                    return createBundleTask(description);
                case MISC:
                    return createMiscTask(description, (MiscContentItem) item, context);
                case MODULE:
                    return createModuleTask(description, context.getCurrentMode() == Mode.ROLLBACK || description.isRolledback());
                default:
                    throw new IllegalStateException();
            }
        }

        static PatchingTask createBundleTask(final PatchingTaskDescription description) {
            return new BundlePatchingTask(description);
        }

        static PatchingTask createModuleTask(final PatchingTaskDescription description, boolean rollback) {
            if (rollback) {
                return new ModuleRollbackTask(description);
            } else {
                final ModificationType type = description.getModificationType();
                if(type == ModificationType.REMOVE) {
                    return new ModuleRemoveTask(description);
                } else {
                    return new ModuleUpdateTask(description);
                }
            }
        }

        static PatchingTask createMiscTask(final PatchingTaskDescription description, final MiscContentItem item, final PatchingTaskContext context) {
            // Create the task
            final File target = context.getTargetFile(item);
            final File backup = context.getBackupFile(item);
            final ModificationType type = description.getModificationType();
            switch (type) {
                case ADD:
                case MODIFY:
                    return new FileUpdateTask(description, target, backup);
                case REMOVE:
                    return new FileRemoveTask(description, target, backup);
                default:
                    throw new IllegalStateException();
            }
        }
    }

}
