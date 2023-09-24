/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import org.jboss.as.patching.PatchingException;

/**
 * @author Emanuel Muckenhuber
 */
public interface PatchMetadataResolver {

    /**
     * Resolve a for a given product name and version.
     *
     * @param name    the product name
     * @param version the product version
     * @return the resolved patch
     * @throws PatchingException for any error
     */
    Patch resolvePatch(String name, String version) throws PatchingException;

}
