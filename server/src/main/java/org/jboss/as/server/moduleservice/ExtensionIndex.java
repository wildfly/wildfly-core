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
     * Find the identifier of an extension module, returning {@code null} if no matching extension is available.
     *
     * @param name             the extension name
     * @param minSpecVersion   the minimum spec version to match, or {@code null} to match any
     * @param minImplVersion   the minimum implementation version to match, or {@code null} to match any
     * @param requiredVendorId the vendor ID to require, or {@code null} to match any
     * @return the module identifier of the extension found.
     * @deprecated Use {@link #findExtensionAsString(String, String, String, String)} instead
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    ModuleIdentifier findExtension(String name, String minSpecVersion, String minImplVersion, String requiredVendorId);

    /**
     * Find the identifier of an extension module, returning {@code null} if no matching extension is available.
     *
     * @param name             the extension name
     * @param minSpecVersion   the minimum spec version to match, or {@code null} to match any
     * @param minImplVersion   the minimum implementation version to match, or {@code null} to match any
     * @param requiredVendorId the vendor ID to require, or {@code null} to match any
     *
     * @return the module identifier of the extension found.
     */
    String findExtensionAsString(String name, String minSpecVersion, String minImplVersion, String requiredVendorId);

    /**
     * Adds an extension that has been deployed to the server
     *
     * @param identifier    The module identifier of the extension
     * @param extensionInfo The extension data
     * @deprecated Use {@link #addDeployedExtension(String, ExtensionInfo)} instead
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    void addDeployedExtension(ModuleIdentifier identifier, ExtensionInfo extensionInfo);

    /**
     * Adds an extension that has been deployed to the server
     *
     * @param identifier    The module identifier of the extension
     * @param extensionInfo The extension data
     */
    void addDeployedExtension(String identifier, ExtensionInfo extensionInfo);

    /**
     * Removes extension information that has been deployed to the server
     *
     * @param name       The extension name
     * @param identifier The extension Identifier
     * @return true if the extension was found and removed
     *
     * @deprecated Use {@link #removeDeployedExtension(String, String)} instead
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    boolean removeDeployedExtension(String name, ModuleIdentifier identifier);

    /**
     * Removes extension information that has been deployed to the server
     *
     * @param name       The extension name
     * @param identifier The extension identifier to remove
     * @return true if the extension was found and removed
     */
    boolean removeDeployedExtension(String name, String identifier);
}
