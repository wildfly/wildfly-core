/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import org.jboss.as.patching.installation.InstalledIdentity;

/**
 * Additional patch information for a rollback
 *
 * @author Emanuel Muckenhuber
 */
public interface RollbackPatch extends Patch {

    /**
     * Get the backed up identity state.
     *
     * @return the installed identity state
     */
    InstalledIdentity getIdentityState();

}
