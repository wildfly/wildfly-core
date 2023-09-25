/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.IoUtils.NO_CONTENT;

import java.io.File;
import java.io.IOException;

import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.BundleItem;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * Base {@linkplain PatchingTask} for module updates.
 *
 * @author Emanuel Muckenhuber
 */
abstract class AbstractModuleTask extends AbstractPatchingTask<ModuleItem> {

    static final String MODULE_XML = "module.xml";

    AbstractModuleTask(PatchingTaskDescription description) {
        super(description, ModuleItem.class);
    }

    @Override
    byte[] backup(PatchingTaskContext context) throws IOException {
        final File[] repoRoots = context.getTargetModulePath();
        final String moduleName = contentItem.getName();
        final String slot = contentItem.getSlot();
        for(final File path : repoRoots) {
            // Find the first module and calculate the hash
            final File modulePath = PatchContentLoader.getModulePath(path, moduleName, slot);
            final File moduleXml = new File(modulePath, MODULE_XML);
            if(moduleXml.exists()) {
                PatchLogger.ROOT_LOGGER.debugf("found in path (%s)", moduleXml.getAbsolutePath());
                context.invalidateRoot(modulePath);
                return HashUtils.hashFile(modulePath);
            }
        }
        return notFound(contentItem);
    }

    protected byte[] notFound(final ModuleItem contentItem) throws IOException{
        return NO_CONTENT;
    }

    static ModuleItem createContentItem(final ModuleItem original, final byte[] contentHash) {
        final ContentType type = original.getContentType();
        if(type == ContentType.BUNDLE) {
            return new BundleItem(original.getName(), original.getSlot(), contentHash);
        } else {
            return new ModuleItem(original.getName(), original.getSlot(), contentHash);
        }
    }

}
