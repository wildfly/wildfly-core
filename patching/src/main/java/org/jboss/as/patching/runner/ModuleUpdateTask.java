/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * Adding or updating a module will add a module in the patch overlay directory {@linkplain org.jboss.as.patching.DirectoryStructure#getModulePatchDirectory(String)}.
 *
 * @author Emanuel Muckenhuber
 */
class ModuleUpdateTask extends AbstractModuleTask {

    ModuleUpdateTask(PatchingTaskDescription description) {
        super(description);
    }

    @Override
    public boolean prepare(final PatchingTaskContext context) throws IOException {
        // Backup
        backupHash = backup(context);
        // If the content is already present just resolve any conflict automatically
        final byte[] contentHash = contentItem.getContentHash();
        if(Arrays.equals(backupHash, contentHash)) {
            return true;
        }
        // See if the content matches our expected target
        final byte[] expected = description.getModification().getTargetHash();
        if(Arrays.equals(backupHash, expected)) {
            // Don't resolve conflicts from the history
            return ! description.hasConflicts();
        }
        // System.out.println("ModuleUpdateTask.prepare " + description.getModificationType() + " backup " + (backupHash == IoUtils.NO_CONTENT));
        // the problem here appears for compact CPs when a module at some point was added then removed and then re-added
        // re-adding will be MODIFY because the removed module will exist on the FS but will be marked as absent in its module.xml
        // so applying re-add (MODIFY) to the version where the module didn't exist will fail
        return false;
    }

    @Override
    byte[] apply(PatchingTaskContext context, PatchContentLoader loader) throws IOException {
        // Copy the new module resources to the patching directory
        final File targetDir = context.getTargetFile(contentItem);
        final File sourceDir = loader.getFile(contentItem);
        if(sourceDir.exists()) {
            // Recursively copy module contents (incl. native libs)
            IoUtils.copyFile(sourceDir, targetDir);
        } else { // ADD an absent module
            // this situation happens when merging ADD and REMOVE modifications
            // which results in an ADD of an absent module
            if(!targetDir.exists() && ! targetDir.mkdirs()) {
                throw PatchLogger.ROOT_LOGGER.cannotCreateDirectory(targetDir.getAbsolutePath());
            }

            final Path moduleXml = targetDir.toPath().resolve(MODULE_XML);
            final ByteArrayInputStream is = new ByteArrayInputStream(PatchUtils.getAbsentModuleContent(contentItem));
            Files.copy(is, moduleXml, StandardCopyOption.REPLACE_EXISTING);
        }
        // return contentItem.getContentHash();
        return HashUtils.hashFile(targetDir);
    }

    @Override
    ContentModification createRollbackEntry(ContentModification original, byte[] targetHash, byte[] itemHash) {
        // Although modules are ignored for rollback, we still keep track of our changes
        final ModuleItem item = createContentItem(contentItem, itemHash);
        final ModificationType type;
        // Check if the module did not exist before. Invalidated patches might include the module already
        // and we need to track that they can be rolled back to the last state
        if (original.getType() != ModificationType.MODIFY && itemHash.length == 0) {
            type = ModificationType.REMOVE;
        } else {
            type = ModificationType.MODIFY;
        }
        return new ContentModification(item, targetHash, type, original.getCondition());
    }

}
