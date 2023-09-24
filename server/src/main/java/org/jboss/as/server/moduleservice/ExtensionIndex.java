/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.moduleservice;

import org.jboss.as.server.deployment.module.ExtensionInfo;
import org.jboss.modules.ModuleIdentifier;

/**
 * An index of available extensions.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ExtensionIndex {

    /**
     * Find an extension, returning {@code null} if no matching extension is available.
     *
     * @param name the extension name
     * @param minSpecVersion the minimum spec version to match, or {@code null} to match any
     * @param minImplVersion the minimum implementation version to match, or {@code null} to match any
     * @param requiredVendorId the vendor ID to require, or {@code null} to match any
     * @return the resource root of the first matched extension
     */
    ModuleIdentifier findExtension(String name, String minSpecVersion, String minImplVersion, String requiredVendorId);

    /**
     * Adds an extension that has been deployed to the server
     *
     * @param identifier The module identifier of the extension
     * @param extensionInfo The extension data
     */
    void addDeployedExtension(ModuleIdentifier identifier, ExtensionInfo extensionInfo);

    /**
     * Removes extension information that has been deployed to the server
     *
     * @param name The extension name
     * @param identifier The extension Identifier
     * @return true if the extension was found and removed
     */
    boolean removeDeployedExtension(String name, ModuleIdentifier identifier);
}
