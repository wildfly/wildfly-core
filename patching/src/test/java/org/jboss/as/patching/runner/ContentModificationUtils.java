/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.Constants.MISC;
import static org.jboss.as.patching.Constants.MODULES;
import static org.jboss.as.patching.HashUtils.hashFile;
import static org.jboss.as.patching.IoUtils.NO_CONTENT;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.patching.metadata.ModificationType.ADD;
import static org.jboss.as.patching.metadata.ModificationType.MODIFY;
import static org.jboss.as.patching.metadata.ModificationType.REMOVE;
import static org.jboss.as.patching.runner.TestUtils.createModule0;
import static org.jboss.as.patching.runner.TestUtils.dump;
import static org.jboss.as.patching.runner.TestUtils.touch;

import java.io.File;
import java.io.IOException;

import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModificationCondition;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class ContentModificationUtils {
    public static ContentModification addModule(File patchDir, String patchElementID, String moduleName, String... resourceContents) throws IOException {
        File modulesDir = newFile(patchDir, patchElementID, MODULES);
        File moduleDir = createModule0(modulesDir, moduleName, resourceContents);
        byte[] newHash = hashFile(moduleDir);
        ContentModification moduleAdded = new ContentModification(new ModuleItem(moduleName, ModuleItem.MAIN_SLOT, newHash), NO_CONTENT, ADD);
        return moduleAdded;
    }

    public static ContentModification addModule(File patchDir, String patchElementID, String moduleName) throws IOException {
        File modulesDir = newFile(patchDir, patchElementID, MODULES);
        File moduleDir = createModule0(modulesDir, moduleName);
        byte[] newHash = hashFile(moduleDir);
        ContentModification moduleAdded = new ContentModification(new ModuleItem(moduleName, ModuleItem.MAIN_SLOT, newHash), NO_CONTENT, ADD);
        return moduleAdded;
    }

    public static ContentModification removeModule(File existingModule) throws IOException {
        return removeModule(existingModule, existingModule.getName());
    }

    public static ContentModification removeModule(File moduleDir, String moduleName) throws IOException {
        byte[] existingHash = hashFile(moduleDir);
        return new ContentModification(new ModuleItem(moduleName, ModuleItem.MAIN_SLOT, NO_CONTENT), existingHash, REMOVE);
    }

    public static ContentModification modifyModule(File patchDir, String patchElementID, File existingModule, String newContent) throws IOException {
        byte[] existingHash = hashFile(existingModule);
        return modifyModule(patchDir, patchElementID, existingModule.getName(), existingHash, newContent);
    }

    public static ContentModification modifyModule(File patchDir, String patchElementID, String moduleName, byte[] existingHash, String newContent) throws IOException {
        File modulesDir = newFile(patchDir, patchElementID, MODULES);
        File modifiedModule = createModule0(modulesDir, moduleName, newContent);
        byte[] updatedHash = hashFile(modifiedModule);
        ContentModification moduleUpdated = new ContentModification(new ModuleItem(moduleName, ModuleItem.MAIN_SLOT, updatedHash), existingHash, MODIFY);
        return moduleUpdated;
    }

    public static ContentModification modifyModule(File patchDir, String patchElementID, String moduleName, byte[] existingHash, TestUtils.ContentTask task) throws IOException {
        File modulesDir = newFile(patchDir, patchElementID, MODULES);
        File modifiedModule = createModule0(modulesDir, moduleName, task);
        byte[] updatedHash = hashFile(modifiedModule);
        ContentModification moduleUpdated = new ContentModification(new ModuleItem(moduleName, ModuleItem.MAIN_SLOT, updatedHash), existingHash, MODIFY);
        return moduleUpdated;
    }

    public static ContentModification addMisc(File patchDir, String patchElementID, String content, String... fileSegments) throws IOException {
        return addMisc(patchDir, patchElementID, content, fileSegments, null);
    }

    public static ContentModification addMisc(File patchDir, String patchElementID, String content, String[] contentSegments, String[] requiredSegments) throws IOException {
        File miscDir = newFile(patchDir, patchElementID, MISC);
        File addedFile = touch(miscDir, contentSegments);
        dump(addedFile, content);
        byte[] newHash = hashFile(addedFile);
        String[] subdir = new String[contentSegments.length -1];
        System.arraycopy(contentSegments, 0, subdir, 0, contentSegments.length - 1);
        final MiscContentItem contentItem = new MiscContentItem(addedFile.getName(), subdir, newHash);

        ModificationCondition condition = null;
        if(requiredSegments != null && requiredSegments.length > 0) {
            subdir = new String[requiredSegments.length -1];
            System.arraycopy(requiredSegments, 0, subdir, 0, requiredSegments.length - 1);
            condition = ModificationCondition.Factory.exists(new MiscContentItem(requiredSegments[requiredSegments.length - 1], subdir, null));
        }
        return new ContentModification(contentItem, NO_CONTENT, ADD, condition);
    }

    public static ContentModification removeMisc(File existingFile, String... fileSegments) throws IOException {
        byte[] existingHash = hashFile(existingFile);
        String[] subdir = new String[0];
        if (fileSegments.length > 0) {
            subdir = new String[fileSegments.length -1];
            System.arraycopy(fileSegments, 0, subdir, 0, fileSegments.length - 1);
        }
        ContentModification fileRemoved = new ContentModification(new MiscContentItem(existingFile.getName(), subdir, NO_CONTENT), existingHash, REMOVE);
        return fileRemoved;
    }

    public static ContentModification modifyMisc(File patchDir, String patchElementID, String modifiedContent, File existingFile, String... fileSegments) throws IOException {
        byte[] existingHash = hashFile(existingFile);
        return modifyMisc(patchDir, patchElementID, modifiedContent, existingHash, fileSegments);
    }

    public static ContentModification modifyMisc(File patchDir, String patchElementID, String modifiedContent, byte[] existingHash, String... fileSegments) throws IOException {
        return modifyMisc(patchDir, patchElementID, modifiedContent, existingHash, fileSegments, null);
    }

    public static ContentModification modifyMisc(File patchDir, String patchElementID, String modifiedContent, byte[] existingHash, String[] fileSegments, String[] requiredSegments) throws IOException {
        File miscDir = newFile(patchDir, patchElementID, MISC);
        File modifiedFile = touch(miscDir, fileSegments);
        dump(modifiedFile, modifiedContent);
        byte[] modifiedHash = hashFile(modifiedFile);
        String[] subdir = new String[0];
        if (fileSegments.length > 0) {
            subdir = new String[fileSegments.length -1];
            System.arraycopy(fileSegments, 0, subdir, 0, fileSegments.length - 1);
        }
        final MiscContentItem item = new MiscContentItem(modifiedFile.getName(), subdir, modifiedHash);

        ModificationCondition condition = null;
        if(requiredSegments != null && requiredSegments.length > 0) {
            subdir = new String[requiredSegments.length -1];
            System.arraycopy(requiredSegments, 0, subdir, 0, requiredSegments.length - 1);
            condition = ModificationCondition.Factory.exists(new MiscContentItem(requiredSegments[requiredSegments.length - 1], subdir, null));
        }

        return new ContentModification(item, existingHash, MODIFY, condition);
    }
}
