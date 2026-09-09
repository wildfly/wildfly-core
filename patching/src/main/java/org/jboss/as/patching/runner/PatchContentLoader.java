/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import java.io.File;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * Content loader for patch contents. When applying a patch the content is loaded from the patch itself; for rollbacks
 * the content will be loaded from the patch history.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class PatchContentLoader {

    public static final String MISC = Constants.MISC;

    public static File getMiscPath(final File miscRoot, final MiscContentItem item) {
        if (miscRoot == null) {
            throw new IllegalStateException();
        }
        File file = miscRoot;
        for (final String path : item.getPath()) {
            file = new File(file, path);
        }
        file = new File(file, item.getName());
        return file;
    }

    public static File getModulePath(File root, ModuleItem item) {
        return getModulePath(root, item.getName(), item.getSlot());
    }

    static File getModulePath(File root, String name, String slot) {
        if (root == null) {
            throw new IllegalStateException();
        }
        final String[] ss = name.split("\\.");
        File file = root;
        for (final String s : ss) {
            file = new File(file, s);
        }
        return new File(file, slot);
    }

}
