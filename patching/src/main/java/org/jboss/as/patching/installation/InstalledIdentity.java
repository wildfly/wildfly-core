/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.patching.Constants;
import org.jboss.as.version.ProductConfig;

/**
 * Information about the installed identity.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class InstalledIdentity {

    /**
     * Get a list of all installed patches.
     *
     * @return the list of all installed patches
     */
    public abstract List<String> getAllInstalledPatches();

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

    /**
     * Load the installation state based on the identity
     *
     * @param installedIdentity the installed identity
     * @return the installation state
     * @throws IOException
     */
    protected static InstallationModificationImpl.InstallationState load(final InstalledIdentity installedIdentity) throws IOException {
        final InstallationModificationImpl.InstallationState state = new InstallationModificationImpl.InstallationState();
        for (final Layer layer : installedIdentity.getLayers()) {
            state.putLayer(layer);
        }
        for (final AddOn addOn : installedIdentity.getAddOns()) {
            state.putAddOn(addOn);
        }
        return state;
    }

    /**
     * Load the layers based on the default setup.
     *
     * @param jbossHome     the jboss home directory
     * @param productConfig the product config
     * @param repoRoots     the repository roots
     * @return the available layers
     * @throws IOException
     */
    public static InstalledIdentity load(final File jbossHome, final ProductConfig productConfig, final File... repoRoots) throws IOException {
        final InstalledImage installedImage = installedImage(jbossHome);
        return load(installedImage, productConfig, Arrays.<File>asList(repoRoots), Collections.<File>emptyList());
    }

    /**
     * Load the InstalledIdentity configuration based on the module.path
     *
     * @param installedImage the installed image
     * @param productConfig  the product config
     * @param moduleRoots    the module roots
     * @param bundleRoots    the bundle roots
     * @return the available layers
     * @throws IOException
     */
    public static InstalledIdentity load(final InstalledImage installedImage, final ProductConfig productConfig,  List<File> moduleRoots, final List<File> bundleRoots) throws IOException {
        return LayersFactory.load(installedImage, productConfig, moduleRoots, bundleRoots);
    }

    protected static InstalledIdentity copy(InstalledIdentity original) throws IOException {
        final InstalledIdentityImpl copy = new InstalledIdentityImpl(original.getIdentity(), original.getAllInstalledPatches(), original.getInstalledImage());
        for (final Layer layer : original.getLayers()) {
            copy.putLayer(layer.getName(), new LayerInfo(layer.getName(), layer.loadTargetInfo(), layer.getDirectoryStructure()));
        }
        for (final AddOn addOn : original.getAddOns()) {
            copy.putAddOn(addOn.getName(), new LayerInfo(addOn.getName(), addOn.loadTargetInfo(), addOn.getDirectoryStructure()));
        }
        return copy;
    }

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
            public File getPatchHistoryDir(String patchId) {
                return new File(getPatchesDir(), patchId);
            }

            @Override
            public File getLayersConf() {
                return layersConf;
            }
        };
    }

}
