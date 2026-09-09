/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Emanuel Muckenhuber
 */
public class InstalledIdentityImpl extends InstalledIdentity {

    private final Identity identity;
    private final InstalledImage installedImage;
    private final List<String> allPatches;
    private final Map<String, Layer> layers = new LinkedHashMap<>();
    private final Map<String, AddOn> addOns = new LinkedHashMap<>();

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
        final List<Layer> layers = new ArrayList<>(this.layers.values());
        return Collections.unmodifiableList(layers);
    }

    @Override
    public Layer getLayer(String layerName) {
        return layers.get(layerName);
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

}
