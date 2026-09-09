/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

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

}
