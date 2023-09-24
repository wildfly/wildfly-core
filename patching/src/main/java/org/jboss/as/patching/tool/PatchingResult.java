/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tool;

import org.jboss.as.patching.PatchInfo;

/**
 * The result when applying a patch.
 *
 * @author Emanuel Muckenhuber
 */
public interface PatchingResult {

    /**
     * Get the processed patch id.
     *
     * @return the patch id
     */
    String getPatchId();

    /**
     * Get the patch info.
     *
     * @return the patch info
     */
    PatchInfo getPatchInfo();

    /**
     * Complete.
     */
    void commit();

    /**
     * Rollback...
     */
    void rollback();

}
