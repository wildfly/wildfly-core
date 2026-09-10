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

    private InstalledIdentity defaultIdentity;

    public InstallationManagerImpl(InstalledImage installedImage, final List<File> moduleRoots, final List<File> bundlesRoots, final ProductConfig productConfig)
            throws IOException {
        defaultIdentity = LayersFactory.load(installedImage, productConfig, moduleRoots, bundlesRoots);
    }

    @Override
    public InstalledIdentity getDefaultIdentity() {
        return defaultIdentity;
    }
}
