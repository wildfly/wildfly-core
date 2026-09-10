/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import static org.jboss.as.patching.Constants.OVERLAYS;

import java.io.File;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.logging.PatchLogger;

/**
 * @author Emanuel Muckenhuber
 */
abstract class LayerDirectoryStructure extends DirectoryStructure {

    abstract File getPatchesMetadata();

    protected File getPatchesMetadata(String name) {
        File root = getModuleRoot();
        if (root == null) {
            root = getBundleRepositoryRoot();
        }
        if (root == null) {
            throw PatchLogger.ROOT_LOGGER.installationInvalidLayerConfiguration(name);
        }
        return root;
    }

    @Override
    public File getInstallationInfo() {
        return new File(getPatchesMetadata(), Constants.INSTALLATION_METADATA);
    }

    @Override
    public File getBundlesPatchDirectory(final String patchId) {
        if (getBundleRepositoryRoot() == null) {
            return null;
        }
        // ${repo-root}/bundles/${layer.type+name}/.overlays/${patch.id}
        final File patches = new File(getBundleRepositoryRoot(), OVERLAYS);
        return new File(patches, patchId);
    }

    @Override
    public File getModulePatchDirectory(final String patchId) {
        if (getModuleRoot() == null) {
            return null;
        }
        // ${repo-root}/modules/${layer.type+name}/.overlays/${patch.id}
        final File patches = new File(getModuleRoot(), OVERLAYS);
        return new File(patches, patchId);
    }

    /**
     * Specific directory structure implementation for the identity.
     */
    abstract static class IdentityDirectoryStructure extends LayerDirectoryStructure {

        @Override
        public final File getBundleRepositoryRoot() {
            return null; // no bundle root associated with the identity
        }

        @Override
        public final File getModuleRoot() {
            return null; // no module root associated with the identity
        }

        @Override
        public File getInstallationInfo() {
            return new File(getPatchesMetadata(), Constants.IDENTITY_METADATA);
        }

        @Override
        protected File getPatchesMetadata() {
            return getInstalledImage().getInstallationMetadata();
        }

    }

}
