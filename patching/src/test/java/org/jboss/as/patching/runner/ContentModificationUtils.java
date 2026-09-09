/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.Constants.MODULES;
import static org.jboss.as.patching.HashUtils.hashFile;
import static org.jboss.as.patching.IoUtils.NO_CONTENT;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.patching.metadata.ModificationType.ADD;
import static org.jboss.as.patching.runner.TestUtils.createModule0;

import java.io.File;
import java.io.IOException;

import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class ContentModificationUtils {
    public static ContentModification addModule(File patchDir, String patchElementID, String moduleName) throws IOException {
        File modulesDir = newFile(patchDir, patchElementID, MODULES);
        File moduleDir = createModule0(modulesDir, moduleName);
        byte[] newHash = hashFile(moduleDir);
        ContentModification moduleAdded = new ContentModification(new ModuleItem(moduleName, ModuleItem.MAIN_SLOT, newHash), NO_CONTENT, ADD);
        return moduleAdded;
    }

}
