/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

/**
 * @author Alexey Loubyansky
 *
 */
public interface Identity extends UpgradeCondition {

    /**
     * Get the target version.
     *
     * @return the target version
     */
    String getVersion();

    <T extends Identity> T forType(Patch.PatchType patchType, Class<T> clazz);

    public interface IdentityUpgrade extends Identity {

        /**
         * Get the resulting version of a release or {@code null} for other patches
         *
         * @return the resulting version
         */
        String getResultingVersion();

    }

}
