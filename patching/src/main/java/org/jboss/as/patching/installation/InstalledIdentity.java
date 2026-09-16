/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.jboss.as.patching.Constants;

/**
 * Information about the installed identity.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class InstalledIdentity {

    /**
     * Get information about the installed identity.
     *
     * @return the identity
     */
    public abstract Identity getIdentity();

    /**
     * Get a layer by name.
     *
     * @param layerName the layer name
     * @return the layer, {@code null} if there is no matching layer
     */
    public abstract Layer getLayer(String layerName);

    /**
     * Get a list of installed layers.
     *
     * @return the installed layers
     */
    public abstract List<Layer> getLayers();

    /**
     * Get an add-on by name.
     *
     * @param addOnName the add-on name
     * @return the add-on, {@code null} if is no matching add-on
     */
    public abstract AddOn getAddOn(String addOnName);

    /**
     * Get a list of installed add-ons.
     *
     * @return the installed add-ons
     */
    public abstract Collection<AddOn> getAddOns();

    /**
     * Get the installed image.
     *
     * @return the installed image
     */
    public abstract InstalledImage getInstalledImage();

    static InstalledImage installedImage(final File jbossHome) {
        final File bundles = new File(jbossHome, Constants.BUNDLES);
        final File modules = new File(jbossHome, Constants.MODULES);
        final File metadata = new File(jbossHome, Constants.INSTALLATION);
        final File layersConf = new File(modules, Constants.LAYERS_CONF);
        return new InstalledImage() {
            @Override
            public File getJbossHome() {
                return jbossHome;
            }

            @Override
            public File getBundlesDir() {
                return bundles;
            }

            @Override
            public File getInstallationMetadata() {
                return metadata;
            }

            @Override
            public File getPatchesDir() {
                return new File(getInstallationMetadata(), Constants.PATCHES);
            }

            @Override
            public File getModulesDir() {
                return modules;
            }

            @Override
            public File getLayersConf() {
                return layersConf;
            }
        };
    }

}
