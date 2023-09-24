/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching;

import java.io.File;

import org.jboss.as.patching.installation.InstalledImage;

/**
 * The patching directory structure.
 *
 * <pre>
 * <code>
 *
 * ${JBOSS_HOME}
 * |-- bin
 * |-- bundles
 * |   |-- system (system bundles contains only bundles, no patches metadata)
 * |   |   |-- layers
 * |   |   |   |-- xyz
 * |   |   |   |   `-- .overlays (overlay directory)
 * |   |   |   |       |-- .overlays (overlay metadata)
 * |   |   |   |       |-- patch-xyz-1
 * |   |   |   |       `-- patch-xyz-2
 * |   |   |   |-- vuw
 * |   |   |   |   `-- .overlays (overlay directory)
 * |   |   |   |       `-- patch-vuw-1
 * |   |   |   `-- base
 * |   |   |       |-- .overlays (overlay directory)
 * |   |   |       |   |-- patch-base-1
 * |   |   |       |   `-- patch-base-2
 * |   |   `-- add-ons
 * |   |       `-- def
 * |   |           `-- .overlays (overlay directory)
 * |   |               |-- patch-def-1
 * |   |               `-- patch-def-2
 * |   |
 * |   `-- my/own/bundle/path/thing
 * |
 * |-- docs
 * |-- modules
 * |   |-- layers.conf (xyz,vuw)
 * |   |-- system (system modules contains only modules, no patches metadata)
 * |   |   |-- layers
 * |   |   |   |-- xyz
 * |   |   |   |    `-- .overlays (overlay directory)
 * |   |   |   |       |-- patch-xyz-1
 * |   |   |   |       `-- patch-xyz-2
 * |   |   |   |-- vuw
 * |   |   |   |    `-- .overlays (overlay directory)
 * |   |   |   |        `-- patch-vuw-1
 * |   |   |   ` -- base
 * |   |   |        |-- .overlays (overlay directory)
 * |   |   |        |   |-- patch-base-1
 * |   |   |        |   `-- patch-base-2
 * |   |   |        |-- org/jboss/as/...
 * |   |   |        `-- org/jboss/as/server/main/module.xml
 * |   |   `-- add-ons
 * |   |       `-- def
 * |   |           `-- .overlays (overlay directory)
 * |   |               |-- patch-def-1
 * |   |               `-- patch-def-2
 * |   |
 * |   `-- my/own/module/root/repo
 * |
 * |-- .installation (metadata directory for the installation)
 * |   |-- identity.conf (patched state for the installed identity)
 * |   |-- patches  (history of the patches applied to the identity)
 * |   |    `-- patch-identity-1
 * |   |       |-- patch.xml
 * |   |       |-- rollback.xml
 * |   |       |-- timestamp
 * |   |       |-- configuration   (configuration backup)
 * |   |       `-- misc            (misc backup)
 * |   |-- layers (metadata for patched layers)
 * |   |   |-- base
 * |   |   |   `-- layer.conf (patched state for the layer)
 * |   |   |-- xyz
 * |   |   |   `-- layer.conf
 * |   |   |-- vuw
 * |   |   |   `-- layer.conf
 * |   `-- add-ons (metadata for patched add-ons)
 * |       `-- def
 * |           `-- layer.conf
 * `-- jboss-modules.jar
 * </code>
 * </pre>
 *
 * Algorithm to build the module path when the server boots:
 *
 * <ol>
 *     <li>let paths be a list of File</li>
 *     <li>for each layer in {@link org.jboss.as.patching.installation.InstalledImage#getLayersConf()} file and "base":</li>
 *     <ol>
 *        <li>read the cumulative-patch-id in {@link org.jboss.as.patching.installation.Layer#loadTargetInfo()#getInstallationInfo()}</li>
 *        <li>append {@link org.jboss.as.patching.installation.Layer#loadTargetInfo()#getModulePatchDirectory(String)} for the cumulative-patch-id (if it exists) to the paths</li>
 *        <li>for each one-off patchIDs in {@link org.jboss.as.patching.installation.Layer#loadTargetInfo()#getInstallationInfo()}</li>
 *        <ol>
 *            <li>append {@link org.jboss.as.patching.installation.Layer#loadTargetInfo()#getModulePatchDirectory(String)} (if it exists) to the paths</li>
 *        </ol>
 *     </ol>
 *     <li>for each addOn in {@link InstalledImage#getModulesDir()}}/system/add-ons</li>
 *     <ol>
 *        <li>read the cumulative-patch-id in {@link org.jboss.as.patching.installation.AddOn#loadTargetInfo()#getInstallationInfo()}</li>
 *        <li>append {@link org.jboss.as.patching.installation.AddOn#loadTargetInfo()#getModulePatchDirectory(String)} for the cumulative-patch-id (if it exists) to the paths</li>
 *        <li>for each one-off patchIDs in {@link org.jboss.as.patching.installation.AddOn#loadTargetInfo()#getInstallationInfo()}</li>
 *        <ol>
 *            <li>append {@link org.jboss.as.patching.installation.AddOn#loadTargetInfo()#getModulePatchDirectory(String)} (if it exists) to the paths</li>
 *        </ol>
 *     </ol>
 *     <li>return paths</li>
 * </ol>
 *
 * Same algorithm applies to build the bundle path.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class DirectoryStructure {

    /**
     * Get the installed image layout.
     *
     * @return the installed image
     */
    public abstract InstalledImage getInstalledImage();

    /**
     * Get the installation metadata.
     *
     * @return the installation metadata file
     */
    public abstract File getInstallationInfo();

    /**
     * Get the bundles repository root.
     *
     * @return the bundle base directory
     */
    public abstract File getBundleRepositoryRoot();

    /**
     * Get the bundles patch directory for a given patch-id.
     *
     * @param patchId the patch-id
     * @return the bundles patch directory
     */
    public abstract File getBundlesPatchDirectory(final String patchId);

    /**
     * Get the module root.
     *
     * @return the module root
     */
    public abstract File getModuleRoot();

    /**
     * Get the modules patch directory for a given patch-id.
     *
     * @param patchId the patch-id
     * @return the modules patch directory
     */
    public abstract File getModulePatchDirectory(final String patchId);

}