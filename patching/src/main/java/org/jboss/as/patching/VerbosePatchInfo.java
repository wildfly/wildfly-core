/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.patching.installation.PatchableTarget.TargetInfo;

/**
 *
 * @author Alexey Loubyansky
 */
public class VerbosePatchInfo implements PatchInfo {

    public static class Builder {

        private String version;
        private String cpId;
        private List<String> patchIds = Collections.emptyList();
        private Map<String, TargetInfo> layers = Collections.emptyMap();
        private Map<String, TargetInfo> addons = Collections.emptyMap();

        private Builder() {
        }

        public Builder setVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder setCumulativePatchId(String cpId) {
            this.cpId = cpId;
            return this;
        }

        public Builder setPatchIds(List<String> patchIds) {
            this.patchIds = patchIds;
            return this;
        }

        public Builder addLayerInfo(String name, TargetInfo info) {
            addInfo(name, info, true);
            return this;
        }

        public Builder addAddOnInfo(String name, TargetInfo info) {
            addInfo(name, info, false);
            return this;
        }

        private void addInfo(String name, TargetInfo info, boolean layer) {
            Map<String, TargetInfo> map = layer ? layers : addons;
            switch(map.size()) {
                case 0:
                    map = Collections.singletonMap(name, info);
                    if(layer) {
                        layers = map;
                    } else {
                        addons = map;
                    }
                    break;
                case 1:
                    map = new HashMap<String, TargetInfo>(map);
                    if(layer) {
                        layers = map;
                    } else {
                        addons = map;
                    }
                default:
                    map.put(name, info);
            }
        }

        public VerbosePatchInfo build() {
            return new VerbosePatchInfo(version, cpId, Collections.unmodifiableList(patchIds),
                    Collections.unmodifiableMap(layers),
                    Collections.unmodifiableMap(addons));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final String version;
    private final String cpId;
    private final List<String> patchIds;
    private final Map<String, TargetInfo> layers;
    private final Map<String, TargetInfo> addons;


    private VerbosePatchInfo(String version, String cpId, List<String> patchIds, Map<String, TargetInfo> layers, Map<String, TargetInfo> addons) {
        this.version = version;
        this.cpId = cpId;
        this.patchIds = patchIds;
        this.layers = layers;
        this.addons = addons;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.patching.PatchInfo#getVersion()
     */
    @Override
    public String getVersion() {
        return version;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.patching.PatchInfo#getCumulativePatchID()
     */
    @Override
    public String getCumulativePatchID() {
        return cpId;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.patching.PatchInfo#getPatchIDs()
     */
    @Override
    public List<String> getPatchIDs() {
        return patchIds;
    }

    public boolean hasLayers() {
        return !layers.isEmpty();
    }

    public Collection<String> getLayerNames() {
        return layers.keySet();
    }

    public TargetInfo getLayerInfo(String name) {
        return layers.get(name);
    }

    public boolean hasAddOns() {
        return !addons.isEmpty();
    }

    public Collection<String> getAddOnNames() {
        return addons.keySet();
    }

    public TargetInfo getAddOnInfo(String name) {
        return addons.get(name);
    }
}
