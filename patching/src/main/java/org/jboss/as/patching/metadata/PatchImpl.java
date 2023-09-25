/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import java.util.List;

import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.logging.PatchLogger;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchImpl implements Patch {

    private final String patchID;
    private final String description;
    private final String link;
    private final Identity identity;
    private final List<PatchElement> elements;
    private final List<ContentModification> modifications;

    public PatchImpl(String patchID, String description, Identity identity,
                     List<PatchElement> elements, List<ContentModification> modifications) {
        this(patchID, description, null, identity, elements, modifications);
    }

    public PatchImpl(String patchID, String description, String link, Identity identity,
                     List<PatchElement> elements, List<ContentModification> modifications) {

        if (!Patch.PATCH_NAME_PATTERN.matcher(patchID).matches()) {
            throw PatchLogger.ROOT_LOGGER.illegalPatchName(patchID);
        }

        this.patchID = patchID;
        this.description = description;
        this.link = link;
        this.identity = identity;
        this.elements = elements;
        this.modifications = modifications;
    }

    @Override
    public String getPatchId() {
        return patchID;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getLink() {
        return link;
    }

    @Override
    public Identity getIdentity() {
        return identity;
    }

    @Override
    public List<PatchElement> getElements() {
        return elements;
    }

    @Override
    public List<ContentModification> getModifications() {
        return modifications;
    }

    public static class RollbackPatchImpl implements RollbackPatch {

        private final Patch patch;
        private final InstalledIdentity installedIdentity;
        public RollbackPatchImpl(Patch patch, InstalledIdentity installedIdentity) {
            this.patch = patch;
            this.installedIdentity = installedIdentity;
        }

        @Override
        public String getPatchId() {
            return patch.getPatchId();
        }

        @Override
        public String getDescription() {
            return patch.getDescription();
        }

        @Override
        public String getLink() {
            return patch.getLink();
        }

        @Override
        public Identity getIdentity() {
            return patch.getIdentity();
        }

        @Override
        public List<PatchElement> getElements() {
            return patch.getElements();
        }

        @Override
        public List<ContentModification> getModifications() {
            return patch.getModifications();
        }

        @Override
        public InstalledIdentity getIdentityState() {
            return installedIdentity;
        }
    }

}
