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

package org.jboss.as.patching.tests;

import static org.jboss.as.patching.IoUtils.NO_CONTENT;
import static org.jboss.as.patching.runner.TestUtils.randomString;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModificationBuilderTarget;
import org.jboss.as.patching.metadata.ModificationCondition;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.runner.ContentModificationUtils;
import org.jboss.as.patching.runner.TestUtils;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractPatchTestBuilder<T> extends ModificationBuilderTarget<T> {

    protected abstract String getPatchId();
    protected abstract File getPatchDir();
    protected abstract T returnThis();

    public T addFile(byte[] resultingHash, final String content, String... path) throws IOException {
        return addFile(resultingHash, content, path, null);
    }

    public T addFile(byte[] resultingHash, final String content, String[] path, String[] requiredPath) throws IOException {
        final ContentModification modification = ContentModificationUtils.addMisc(getPatchDir(), getPatchId(), content, path, requiredPath);
        return addContentModification(modification, resultingHash);
    }

    public T addFileWithRandomContent(byte[] resultingHash, String... path) throws IOException {
        return addFileWithRandomContent(resultingHash, path, null);
    }

    public T addFileWithRandomContent(byte[] resultingHash, String[] path, String[] requiredPath) throws IOException {
        return addFile(resultingHash, randomString(), path, requiredPath);
    }

    public T updateFileWithRandomContent(byte[] existingHash, byte[] resultingHash, String... path) throws IOException {
        return updateFileWithRandomContent(existingHash, resultingHash, path, null);
    }

    public T updateFileWithRandomContent(byte[] existingHash, byte[] resultingHash, String[] path, String[] requiredPath) throws IOException {
        final ContentModification modification = ContentModificationUtils.modifyMisc(
                getPatchDir(), getPatchId(), randomString(), Arrays.copyOf(existingHash, existingHash.length), path, requiredPath);
        return addContentModification(modification, resultingHash);
    }

    public T removeFile(byte[] existingHash, String... path) {
        final String name = path[path.length - 1];
        removeFile(name, Arrays.asList(Arrays.copyOf(path, path.length - 1)), existingHash, false);
        return returnThis();
    }

    public T removeFile(byte[] existingHash, String[] path, String[] requiredPath) {
        return removeFile(path[path.length - 1], Arrays.copyOf(path, path.length - 1), existingHash, false, requiredPath);
    }

    public T removeFile(final String name, final String[] path, final byte[] existingHash, final boolean isDirectory, final String[] requiredPath) {
        final ContentItem item = createMiscItem(name, Arrays.asList(path), NO_CONTENT, isDirectory);
        ModificationCondition condition = null;
        if(requiredPath != null && requiredPath.length > 0) {
            final String[] subdir = new String[requiredPath.length -1];
            System.arraycopy(requiredPath, 0, subdir, 0, requiredPath.length - 1);
            condition = ModificationCondition.Factory.exists(new MiscContentItem(requiredPath[requiredPath.length - 1], subdir, null));
        }
        return addContentModification(new ContentModification(item, existingHash, ModificationType.REMOVE, condition));
    }

    public T addModuleWithContent(final String moduleName, byte[] resultingHash, final String... resourceContents) throws IOException {
        final ContentModification modification = ContentModificationUtils.addModule(getPatchDir(), getPatchId(), moduleName, resourceContents);
        return addContentModification(modification, resultingHash);
    }

    public T addModuleWithRandomContent(final String moduleName, byte[] resultingHash) throws IOException {
        final ContentModification modification = ContentModificationUtils.addModule(getPatchDir(), getPatchId(), moduleName, randomString());
        return addContentModification(modification, resultingHash);
    }

    public T updateModuleWithRandomContent(final String moduleName, byte[] existingHash, byte[] resultingHash) throws IOException {
        final ContentModification modification = ContentModificationUtils.modifyModule(getPatchDir(), getPatchId(), moduleName, existingHash, randomString());
        return addContentModification(modification, resultingHash);
    }

    public T updateModule(final String moduleName, byte[] existingHash, byte[] resultingHash, final TestUtils.ContentTask task) throws IOException {
        final ContentModification modification = ContentModificationUtils.modifyModule(getPatchDir(), getPatchId(), moduleName, existingHash, task);
        return addContentModification(modification, resultingHash);
    }

    protected T addContentModification(final ContentModification modification, byte[] resultingHash) {
        addContentModification(modification);
        contentHash(modification, resultingHash);
        return returnThis();
    }

    static void contentHash(final ContentModification modification, byte[] resultingHash) {
        if (resultingHash != null) {
            final byte[] contentHash = modification.getItem().getContentHash();
            System.arraycopy(contentHash, 0, resultingHash, 0, contentHash.length);
        }
    }

}
