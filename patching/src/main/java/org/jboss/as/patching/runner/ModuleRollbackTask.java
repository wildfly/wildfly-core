/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import java.io.IOException;

import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * Task used to find the active modules and record them as part of the invalidation roots.
 *
 * @author Emanuel Muckenhuber
 */
class ModuleRollbackTask extends AbstractModuleTask {

    public ModuleRollbackTask(PatchingTaskDescription description) {
        super(description);
    }

    @Override
    protected byte[] notFound(ModuleItem contentItem) throws IOException {
        // Maybe just don't record the original ADD as part of the history?
        if (description.getModificationType() == ModificationType.REMOVE) {
            return contentItem.getContentHash();
        }
        return super.notFound(contentItem);
    }

    @Override
    byte[] apply(final PatchingTaskContext context, final PatchContentLoader loader) throws IOException {
        return getContentItem().getContentHash();
    }

    @Override
    ContentModification createRollbackEntry(ContentModification original, byte[] targetHash, byte[] itemHash) {
        return null;
    }
}
