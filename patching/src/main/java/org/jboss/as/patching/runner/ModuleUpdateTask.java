/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.IoUtils.copy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
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
            if(! targetDir.mkdirs()) {
                throw PatchLogger.ROOT_LOGGER.cannotCreateDirectory(targetDir.getAbsolutePath());
            }
            final File moduleXml = new File(targetDir, MODULE_XML);
            final ByteArrayInputStream is = new ByteArrayInputStream(PatchUtils.getAbsentModuleContent(contentItem));
            try {
                return copy(is, moduleXml);
            } finally {
                IoUtils.safeClose(is);
            }
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
        return new ContentModification(item, targetHash, type);
    }

}
