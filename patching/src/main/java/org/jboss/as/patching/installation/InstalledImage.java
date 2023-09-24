/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.io.File;

/**
 * The installed image.
 *
 * @author Emanuel Muckenhuber
 */
public interface InstalledImage {

    /**
     * Get the jboss home.
     *
     * @return the jboss home
     */
    File getJbossHome();

    /**
     * Get the app-client directory.
     *
     * @return the app client dir
     */
    File getAppClientDir();

    /**
     * Get the bundles directory.
     *
     * @return the bundles directory
     */
    File getBundlesDir();

    /**
     * Get the domain directory.
     *
     * @return the domain dir
     */
    File getDomainDir();

    /**
     * Get the installation metadata directory.
     *
     * @return the patches metadata dir
     */
    File getInstallationMetadata();

    /**
     * Get the patch history dir.
     *
     * @param patchId the patch id
     * @return the patch history dir
     */
    File getPatchHistoryDir(String patchId);

    /**
     * Get the patches history root directory.
     *
     * @return the patch root directory
     */
    File getPatchesDir();

    /**
     * Get the modules directory.
     *
     * @return the modules dir
     */
    File getModulesDir();

    /**
     * Get the standalone dir.
     *
     * @return the standalone dir
     */
    File getStandaloneDir();

    /**
     * Get the path to the layers.conf file.
     *
     * @return the layers.conf path
     */
    File getLayersConf();

}
