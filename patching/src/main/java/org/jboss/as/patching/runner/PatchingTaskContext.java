/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import java.io.File;

import org.jboss.as.patching.metadata.ContentItem;

/**
 * @author Emanuel Muckenhuber
 */
public interface PatchingTaskContext {

    enum Mode {

        APPLY,
        UNDO,
        ROLLBACK,
        ;

    }

    /**
     * Get the target location for a given content item.
     *
     * @param item the content item
     * @return the target location
     */
    File getTargetFile(ContentItem item);

}
