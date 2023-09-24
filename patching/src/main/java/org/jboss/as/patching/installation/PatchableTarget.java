/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.jboss.as.patching.DirectoryStructure;

/**
 * A patchable target.
 *
 * @author Emanuel Muckenhuber
 */
public interface PatchableTarget {

    /**
     * The name of the target.
     *
     * @return  name of the target
     */
    String getName();

    /**
     * Load the target info.
     *
     * @return the target info
     */
    TargetInfo loadTargetInfo() throws IOException;

    /**
     * The directory structure for this target.
     *
     * @return the directory structure
     */
    DirectoryStructure getDirectoryStructure();

    public interface TargetInfo {

        /**
         * Get the cumulative patch id.
         *
         * @return the release patch id
         */
        String getCumulativePatchID();

        /**
         * Get the active one-off patches.
         *
         * @return the active one-off patches
         */
        List<String> getPatchIDs();

        /**
         * Get the layer properties.
         *
         * @return the layer properties
         */
        Properties getProperties();

        /**
         * The directory structure for this target.
         *
         * @return the directory structure
         */
        DirectoryStructure getDirectoryStructure();

    }

}
