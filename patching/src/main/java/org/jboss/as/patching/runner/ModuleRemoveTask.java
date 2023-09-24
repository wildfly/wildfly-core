/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.IoUtils.copy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * Removing a module will create a module.xml containing a <module-absent /> element, which
 * will trigger a {@linkplain org.jboss.modules.ModuleNotFoundException} when the
 * {@linkplan org.jboss.modules.ModuleLoader} tries to load the module.
 *
 * @author Emanuel Muckenhuber
 */
class ModuleRemoveTask extends AbstractModuleTask {

    ModuleRemoveTask(PatchingTaskDescription description) {
        super(description);
    }

    @Override
    protected boolean failOnContentMismatch(PatchingTaskContext context) {
        return false;
    }

    @Override
    byte[] apply(PatchingTaskContext context, PatchContentLoader loader) throws IOException {
        final File targetDir = context.getTargetFile(contentItem);
        if(!targetDir.exists() && ! targetDir.mkdirs()) {
            throw PatchLogger.ROOT_LOGGER.cannotCreateDirectory(targetDir.getAbsolutePath());
        }
        final File moduleXml = new File(targetDir, MODULE_XML);
        try (final ByteArrayInputStream is = new ByteArrayInputStream(PatchUtils.getAbsentModuleContent(contentItem))){
            return copy(is, moduleXml);
        }
    }

    @Override
    protected ContentModification getOriginalModification(byte[] targetHash, byte[] itemHash) {
        final ModuleItem original = getContentItem();
        final ModuleItem item = new ModuleItem(original.getName(), original.getSlot(), targetHash);
        return new ContentModification(item, description.getModification().getTargetHash(), ModificationType.MODIFY, description.getModification().getCondition());
    }

    @Override
    ContentModification createRollbackEntry(ContentModification original, byte[] targetHash, byte[] itemHash) {
        final ModuleItem item = createContentItem(contentItem, itemHash);
        return new ContentModification(item, targetHash, ModificationType.MODIFY, original.getCondition());
    }
}
