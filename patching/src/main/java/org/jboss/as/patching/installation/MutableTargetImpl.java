/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.runner.PatchUtils;

/**
 * @author Emanuel Muckenhuber
 */
class MutableTargetImpl implements InstallationManager.MutablePatchingTarget {

    private final DirectoryStructure structure;
    private final PatchableTarget.TargetInfo current;
    // The mutable state
    private final List<String> patchIds;
    private final Properties properties;
    private String cumulativeID;
    private boolean modified = false;

    // this attribute will be null for layers and add-ons in their current implementation
    private String version;

    private Set<String> rolledback = Collections.emptySet();

    MutableTargetImpl(PatchableTarget.TargetInfo current) {
        this(current, null);
    }

    MutableTargetImpl(PatchableTarget.TargetInfo current, String currentVersion) {
        this.current = current;
        this.structure = current.getDirectoryStructure();
        this.cumulativeID = current.getCumulativePatchID();
        this.patchIds = new ArrayList<String>(current.getPatchIDs());
        this.properties = new Properties(current.getProperties());
        this.version = currentVersion;
    }

    @Override
    public boolean isApplied(String patchId) {
        if (cumulativeID.equals(patchId)) {
            return true;
        }
        return patchIds.contains(patchId);
    }

    @Override
    public void rollback(final String patchId) {
        if (!patchIds.remove(patchId)) {
            if (patchId.equals(cumulativeID)) {
                cumulativeID = Constants.NOT_PATCHED;
            } else {
                throw new IllegalStateException("cannot rollback not-applied patch " + patchId); // internal wrong usage, no i18n
            }
        } else {
            switch(rolledback.size()) {
                case 0:
                    rolledback = Collections.singleton(patchId);
                    break;
                case 1:
                    rolledback = new HashSet<String>(rolledback);
                default:
                    rolledback.add(patchId);
            }
        }
        modified = true;
    }

    @Override
    public boolean isRolledback(final String patchId) {
        return rolledback.contains(patchId);
    }

    @Override
    public void apply(String patchId, Patch.PatchType patchType) {
        if (patchType == Patch.PatchType.CUMULATIVE) {
            if (!patchIds.isEmpty()) {
                throw new IllegalStateException("cannot apply cumulative patch if there are other patches applied " +patchIds); // internal wrong usage, no i18n
            }
            cumulativeID = patchId;
        } else {
            patchIds.add(0, patchId);
        }
        modified = true;
    }

    @Override
    public String getCumulativePatchID() {
        return current.getCumulativePatchID();
    }

    @Override
    public List<String> getPatchIDs() {
        return current.getPatchIDs();
    }

    @Override
    public Properties getProperties() {
        return current.getProperties();
    }

    @Override
    public DirectoryStructure getDirectoryStructure() {
        return structure;
    }

    public String getVersion() {
        return version;
    }

    public void setResultingVersion(String version) {
        this.version = version;
    }

    protected Properties getMutableProperties() {
        return properties;
    }

    protected void persist() throws IOException {
        if (modified) {
            // persist the state for bundles and modules directory
            persist(cumulativeID, patchIds, properties);
        }
    }

    protected void restore() throws IOException {
        if (modified) {
            // persist the state for bundles and modules directory
            persist(current.getCumulativePatchID(), current.getPatchIDs(), current.getProperties());
        }
    }

    protected void persist(final String cumulativeID, final List<String> patches, final Properties properties) throws IOException {
        assert cumulativeID != null;
        // Create the parent
        IoUtils.mkdir(structure.getInstallationInfo().getParentFile());

        final List<String> consolidate = new ArrayList<String>();
        consolidate.addAll(patches);
        if (!Constants.BASE.equals(cumulativeID)) {
            consolidate.add(cumulativeID);
        }

        if (structure.getModuleRoot() != null) {
            final File overlays = new File(structure.getModuleRoot(), Constants.OVERLAYS);
            final File refs = new File(overlays, Constants.OVERLAYS);
            PatchUtils.writeRefs(refs, consolidate);
        }

        // Update the properties
        properties.put(Constants.CUMULATIVE, cumulativeID);
        properties.put(Constants.PATCHES, PatchUtils.asString(patches));
        if(version != null) {
            properties.put(Constants.CURRENT_VERSION, version);
        }

        // Write layer.conf
        PatchUtils.writeProperties(structure.getInstallationInfo(), properties);
    }

    @Override
    public PatchableTarget.TargetInfo getModifiedState() {
        if (modified) {
            return new LayerInfo.TargetInfoImpl(properties, cumulativeID, patchIds, structure);
        } else {
            return current;
        }
    }

}
