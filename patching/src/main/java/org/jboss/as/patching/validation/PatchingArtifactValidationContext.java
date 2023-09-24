/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import org.jboss.as.patching.installation.InstalledIdentity;

/**
 * @author Alexey Loubyansky
 *
 */
public interface PatchingArtifactValidationContext {

    /**
     * Get the error handler.
     *
     * @return the error handler
     */
    PatchingValidationErrorHandler getErrorHandler();

    /**
     * Get the installed identity.
     *
     * @return the installed identity
     */
    InstalledIdentity getInstalledIdentity();

    void setCurrentPatchIdentity(InstalledIdentity currentPatchIdentity);

}
