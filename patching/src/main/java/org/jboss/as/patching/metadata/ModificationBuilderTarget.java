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
     * Add a bundle.
     *
     * @param moduleName the module name
     * @param slot the module slot
     * @param newHash the new hash of the added content
     * @return the builder
     */
    public T addBundle(final String moduleName, final String slot, final byte[] newHash) {
        final ContentItem item = createBundleItem(moduleName, slot, newHash);
        addContentModification(createContentModification(item, ModificationType.ADD, NO_CONTENT));
        return returnThis();
    }

    /**
     * Modify a bundle.
     *
     * @param moduleName the module name
     * @param slot the module slot
     * @param existingHash the existing hash
     * @param newHash the new hash of the modified content
     * @return the builder
     */
    public T modifyBundle(final String moduleName, final String slot, final byte[] existingHash, final byte[] newHash) {
        final ContentItem item = createBundleItem(moduleName, slot, newHash);
        addContentModification(createContentModification(item, ModificationType.MODIFY, existingHash));
        return returnThis();
    }

    /**
     * Remove a bundle.
     *
     * @param moduleName the module name
     * @param slot the module slot
     * @param existingHash the existing hash
     * @return the builder
     */
    public T removeBundle(final String moduleName, final String slot, final byte[] existingHash) {
        final ContentItem item = createBundleItem(moduleName, slot, NO_CONTENT);
        addContentModification(createContentModification(item, ModificationType.REMOVE, existingHash));
        return returnThis();
    }

    /**
     * Add a misc file.
     *
     * @param name the file name
     * @param path the relative path
     * @param newHash the new hash of the added content
     * @param isDirectory whether the file is a directory or not
     * @return the builder
     */
    public T addFile(final String name, final List<String> path, final byte[] newHash, final boolean isDirectory) {
        return addFile(name, path, newHash, isDirectory, null);
    }

    public T addFile(final String name, final List<String> path, final byte[] newHash, final boolean isDirectory, ModificationCondition condition) {
        final ContentItem item = createMiscItem(name, path, newHash, isDirectory);
        addContentModification(createContentModification(item, ModificationType.ADD, NO_CONTENT, condition));
        return returnThis();
    }

    /**
     * Modify a misc file.
     *
     * @param name the file name
     * @param path the relative path
     * @param existingHash the existing hash
     * @param newHash the new hash of the modified content
     * @param isDirectory whether the file is a directory or not
     * @return the builder
     */
    public T modifyFile(final String name, final List<String> path, final byte[] existingHash, final byte[] newHash, final boolean isDirectory) {
        return modifyFile(name, path, existingHash, newHash, isDirectory, null);
    }

    public T modifyFile(final String name, final List<String> path, final byte[] existingHash, final byte[] newHash, final boolean isDirectory, ModificationCondition condition) {
        final ContentItem item = createMiscItem(name, path, newHash, isDirectory);
        addContentModification(createContentModification(item, ModificationType.MODIFY, existingHash, condition));
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


    /**
     * Add a module.
     *
     * @param moduleName the module name
     * @param slot the module slot
     * @param newHash the new hash of the added content
     * @return the builder
     */
    public T addModule(final String moduleName, final String slot, final byte[] newHash) {
        final ContentItem item = createModuleItem(moduleName, slot, newHash);
        addContentModification(createContentModification(item, ModificationType.ADD, NO_CONTENT));
        return returnThis();
    }

    /**
     * Modify a module.
     *
     * @param moduleName the module name
     * @param slot the module slot
     * @param existingHash the existing hash
     * @param newHash the new hash of the modified content
     * @return the builder
     */
    public T modifyModule(final String moduleName, final String slot, final byte[] existingHash, final byte[] newHash) {
        final ContentItem item = createModuleItem(moduleName, slot, newHash);
        addContentModification(createContentModification(item, ModificationType.MODIFY, existingHash));
        return returnThis();
    }

    /**
     * Remove a module.
     *
     * @param moduleName the module name
     * @param slot the module slot
     * @param existingHash the existing hash
     * @return the builder
     */
    public T removeModule(final String moduleName, final String slot, final byte[] existingHash) {
        final ContentItem item = createModuleItem(moduleName, slot, NO_CONTENT);
        addContentModification(createContentModification(item, ModificationType.REMOVE, existingHash));
        return returnThis();
    }

    public void setContentItemFilter(final ContentItemFilter filter) {
        this.itemFilter = filter;
    }

    protected ContentModification createContentModification(final ContentItem item, final ModificationType type, final byte[] existingHash) {
        return createContentModification(item, type, existingHash, null);
    }

    protected ContentModification createContentModification(final ContentItem item, final ModificationType type, final byte[] existingHash, ModificationCondition condition) {
        return new ContentModification(item, existingHash, type, condition);
    }

    protected MiscContentItem createMiscItem(final String name, final List<String> path, final byte[] newHash, final boolean isDirectory) {
        return new MiscContentItem(name, path, newHash, isDirectory);
    }

    protected ModuleItem createBundleItem(final String moduleName, final String slot, final byte[] hash) {
        return new BundleItem(moduleName, slot, hash);
    }

    protected ModuleItem createModuleItem(final String moduleName, final String slot, final byte[] hash) {
        return new ModuleItem(moduleName, slot, hash);
    }

}
