/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jboss.as.version.ProductConfig;

/**
 * The installation manager.
 *
 * @author Emanuel Muckenhuber
 */
public class InstallationManagerImpl extends InstallationManager {

    private final InstalledImage installedImage;
    private InstalledIdentity defaultIdentity;

    private final List<File> bundleRoots;

    public InstallationManagerImpl(InstalledImage installedImage, final List<File> moduleRoots, final List<File> bundlesRoots, final ProductConfig productConfig)
            throws IOException {
        this.installedImage = installedImage;

        this.bundleRoots = bundlesRoots;

        defaultIdentity = LayersFactory.load(installedImage, productConfig, moduleRoots, bundleRoots);
    }

    @Override
    public InstalledIdentity getDefaultIdentity() {
        return defaultIdentity;
    }

    @Override
    public InstalledImage getInstalledImage() {
        return installedImage;
    }
}
