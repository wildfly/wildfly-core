/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import static org.jboss.as.patching.IoUtils.NO_CONTENT;

import java.util.List;

import org.jboss.as.patching.runner.ContentItemFilter;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class ModificationBuilderTarget<T> {

    private ContentItemFilter itemFilter;
    protected ModificationBuilderTarget() {
        this(ContentItemFilter.ALL);
    }

    protected ModificationBuilderTarget(ContentItemFilter itemFilter) {
        this.itemFilter = itemFilter;
    }

    protected abstract T internalAddModification(final ContentModification modification);

    /**
     * @return this
     */
    protected abstract T returnThis();

    /**
     * Add a content modification.
     *
     * @param modification the content modification
     */
    public T addContentModification(final ContentModification modification) {
        if (itemFilter.accepts(modification.getItem())) {
            internalAddModification(modification);
        }
        return returnThis();
    }

    /**
     * Remove a misc file.
     *
     * @param name the file name
     * @param path the relative path
     * @param existingHash the existing hash
     * @param isDirectory whether the file is a directory or not
     * @return the builder
     */
    public T removeFile(final String name, final List<String> path, final byte[] existingHash, final boolean isDirectory) {
        return removeFile(name, path, existingHash, isDirectory, null);
    }

    public T removeFile(final String name, final List<String> path, final byte[] existingHash, final boolean isDirectory, ModificationCondition condition) {
        final ContentItem item = createMiscItem(name, path, NO_CONTENT, isDirectory);
        addContentModification(createContentModification(item, ModificationType.REMOVE, existingHash, condition));
        return returnThis();
    }

    protected ContentModification createContentModification(final ContentItem item, final ModificationType type, final byte[] existingHash, ModificationCondition condition) {
        return new ContentModification(item, existingHash, type, condition);
    }

    protected MiscContentItem createMiscItem(final String name, final List<String> path, final byte[] newHash, final boolean isDirectory) {
        return new MiscContentItem(name, path, newHash, isDirectory);
    }

}
