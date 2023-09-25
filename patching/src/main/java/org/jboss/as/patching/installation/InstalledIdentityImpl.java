/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.patching.DirectoryStructure;

/**
 * @author Emanuel Muckenhuber
 */
public class InstalledIdentityImpl extends InstalledIdentity {

    private Identity identity;
    private final InstalledImage installedImage;
    private List<String> allPatches;
    private Map<String, Layer> layers = new LinkedHashMap<String, Layer>();
    private Map<String, AddOn> addOns = new LinkedHashMap<String, AddOn>();

    protected InstalledIdentityImpl(final Identity identity, final List<String> allPatches, final InstalledImage installedImage) {
        this.identity = identity;
        this.installedImage = installedImage;
        this.allPatches = Collections.unmodifiableList(allPatches);
    }

    @Override
    public List<String> getAllInstalledPatches() {
        return allPatches;
    }

    @Override
    public Identity getIdentity() {
        return identity;
    }

    @Override
    public List<Layer> getLayers() {
        final List<Layer> layers = new ArrayList<Layer>(this.layers.values());
        return Collections.unmodifiableList(layers);
    }

    @Override
    public List<String> getLayerNames() {
        final List<String> layerNames = new ArrayList<String>(layers.keySet());
        return Collections.unmodifiableList(layerNames);
    }

    @Override
    public Layer getLayer(String layerName) {
        return layers.get(layerName);
    }

    @Override
    public Collection<String> getAddOnNames() {
        return Collections.unmodifiableCollection(this.addOns.keySet());
    }

    @Override
    public AddOn getAddOn(String addOnName) {
        return addOns.get(addOnName);
    }

    @Override
    public Collection<AddOn> getAddOns() {
        return Collections.unmodifiableCollection(this.addOns.values());
    }

    @Override
    public InstalledImage getInstalledImage() {
        return installedImage;
    }

    protected Layer putLayer(final String name, final Layer layer) {
        return layers.put(name, layer);
    }

    protected AddOn putAddOn(final String name, final AddOn addOn) {
        return addOns.put(name, addOn);
    }

    /**
     * Update the installed identity using the modified state from the modification.
     *
     * @param name the identity name
     * @param modification the modification
     * @param state the installation state
     * @return the installed identity
     */
    @Override
    protected void updateState(final String name, final InstallationModificationImpl modification, final InstallationModificationImpl.InstallationState state) {
        final PatchableTarget.TargetInfo identityInfo = modification.getModifiedState();
        this.identity = new Identity() {
            @Override
            public String getVersion() {
                return modification.getVersion();
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public TargetInfo loadTargetInfo() throws IOException {
                return identityInfo;
            }

            @Override
            public DirectoryStructure getDirectoryStructure() {
                return modification.getDirectoryStructure();
            }
        };

        this.allPatches = Collections.unmodifiableList(modification.getAllPatches());
        this.layers.clear();
        for (final Map.Entry<String, MutableTargetImpl> entry : state.getLayers().entrySet()) {
            final String layerName = entry.getKey();
            final MutableTargetImpl target = entry.getValue();
            putLayer(layerName, new LayerInfo(layerName, target.getModifiedState(), target.getDirectoryStructure()));
        }
        this.addOns.clear();
        for (final Map.Entry<String, MutableTargetImpl> entry : state.getAddOns().entrySet()) {
            final String addOnName = entry.getKey();
            final MutableTargetImpl target = entry.getValue();
            putAddOn(addOnName, new LayerInfo(addOnName, target.getModifiedState(), target.getDirectoryStructure()));
        }
    }
}
