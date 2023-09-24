/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching;

import java.util.Collection;

import org.jboss.as.patching.metadata.ContentItem;

/**
 * The exception is thrown when a patch could not be applied or
 * rolled back because of the content conflicts.
 * E.g. if the actual hash codes of the content on the file system
 * don't match the expected values.
 *
 * @author Alexey Loubyansky
 */
public class ContentConflictsException extends PatchingException {

    private static final long serialVersionUID = -6654143665639437807L;

    private final Collection<ContentItem> conflicts;

    public ContentConflictsException(Collection<ContentItem> conflicts) {
        this("Conflicts detected", conflicts);
    }

    public ContentConflictsException(String msg, Collection<ContentItem> conflicts) {
        super(msg + ": " + conflicts);
        this.conflicts = conflicts;
    }

    public Collection<ContentItem> getConflicts() {
        return conflicts;
    }
}
