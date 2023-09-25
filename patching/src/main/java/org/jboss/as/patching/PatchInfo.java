/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching;

import java.util.List;

/**
 * Basic patch information.
 *
 * @author Emanuel Muckenhuber
 */
public interface PatchInfo {

    /**
     * Get the current version.
     *
     * @return the current version
     */
    String getVersion();

    /**
     * The cumulative patch id.
     *
     * @return the release patch id
     */
    String getCumulativePatchID();

    /**
     * Get active patch ids.
     *
     * @return the patch ids
     */
    List<String> getPatchIDs();

}
