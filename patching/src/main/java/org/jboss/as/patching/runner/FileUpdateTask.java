/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.IoUtils.NO_CONTENT;

import java.io.File;
import java.util.Arrays;

import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModificationType;

/**
 * Task modifying an existing file.
 *
 * @author Emanuel Muckenhuber
 */
final class FileUpdateTask extends AbstractFileTask {

    FileUpdateTask(PatchingTaskDescription description, File target, File backup) {
        super(description, target, backup);
    }

    @Override
    ContentModification createRollbackEntry(ContentModification original, MiscContentItem item, byte[] targetHash) {
        final ModificationType type;
        if (Arrays.equals(NO_CONTENT, item.getContentHash()) && !backup.exists()) {
            type = ModificationType.REMOVE;
        } else {
            type = ModificationType.MODIFY;
        }
        return new ContentModification(item, targetHash, type, original.getCondition());
    }

    protected ContentModification getOriginalModification(byte[] targetHash, byte[] itemHash) {
        final ContentModification original = super.getOriginalModification(targetHash, itemHash);
        final ModificationType type;
        if (Arrays.equals(NO_CONTENT, itemHash) && !backup.exists()) {
            type = ModificationType.ADD;
        } else {
            type = ModificationType.MODIFY;
        }
        return new ContentModification(original.getItem(), original.getTargetHash(), type, original.getCondition());
    }
}
