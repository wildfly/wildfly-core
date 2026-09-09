/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.metadata.LayerType;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.version.ProductConfig;

/**
 * The installation manager, basically represents a mutable {@code InstalledIdentity}.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class InstallationManager {

    public abstract InstalledIdentity getDefaultIdentity();

    public abstract InstalledIdentity getInstalledIdentity(String productName, String productVersion) throws PatchingException;

    public abstract InstalledImage getInstalledImage();

    public interface InstallationModification extends MutablePatchingTarget {

        /**
         * Get the identity name.
         *
         * @return the identity name
         */
        String getName();

        /**
         * Get the current version of the identity.
         *
         * @return the identity version
         */
        String getVersion();

        /**
         * Resolve a target for patching.
         *
         * @param name the layer name
         * @param type the layer type
         * @return the patching target
         */
        MutablePatchingTarget resolve(String name, LayerType type);

        /**
         * Complete the modifications.
         */
        void complete();

        /**
         * Cancel the modifications. The installation is unchanged.
         */
        void cancel();

    }

    public interface MutablePatchingTarget extends PatchableTarget.TargetInfo {

        /**
         * Rollback an applied patch.
         *
         * @param patchId the patch id to rollback
         */
        void rollback(String patchId);

        /**
         * Apply a patch.
         *
         * @param patchId   the patch id
         * @param patchType the patch type
         */
        void apply(String patchId, Patch.PatchType patchType);

    }

    /**
     * Load the default installation manager implementation.
     *
     * @param jbossHome     the jboss home directory
     * @param moduleRoots   the module roots
     * @param bundlesRoots  the bundle roots
     * @param productConfig the product config
     * @return the installation manager implementation
     * @throws IOException
     */
    public static InstallationManager load(final File jbossHome, final List<File> moduleRoots, final List<File> bundlesRoots, final ProductConfig productConfig) throws IOException {
        final InstalledImage installedImage = InstalledIdentity.installedImage(jbossHome);
        return new InstallationManagerImpl(installedImage, moduleRoots, bundlesRoots, productConfig);
    }

}
