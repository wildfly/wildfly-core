/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import java.util.List;

/**
 * @author Emanuel Muckenhuber
 */
public interface BundledPatch {

    /**
     * Get the patches
     *
     * @return
     */
    List<BundledPatchEntry> getPatches();

    class BundledPatchEntry {

        private final String patchId;
        private final String patchPath;

        public BundledPatchEntry(String patchId, String patchPath) {
            this.patchId = patchId;
            this.patchPath = patchPath;
        }

        public String getPatchId() {
            return patchId;
        }

        public String getPatchPath() {
            return patchPath;
        }
    }

}
