/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import java.util.Collection;

/**
 * @author Emanuel Muckenhuber
 */
public interface UpgradeCondition {

    /**
     * Patch element provider name.
     *
     * @return  patch element provider name
     */
    String getName();

    /**
     * Get the patch type.
     *
     * @return the patch type
     */
    Patch.PatchType getPatchType();

    /**
     * List of the applied patch elements to this provider.
     *
     * @return  list of id's of the patch elements applied to this provider
     */
    Collection<String> getRequires();

    /**
     * Get a list of patch-ids, this patch is incompatible with.
     *
     * @return a list of incompatible patches
     */
    Collection<String> getIncompatibleWith();

}
