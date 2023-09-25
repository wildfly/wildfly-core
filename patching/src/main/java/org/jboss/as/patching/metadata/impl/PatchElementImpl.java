/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata.impl;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.metadata.PatchElementProvider;

/**
 * @author Alexey Loubyansky
 *
 */
public class PatchElementImpl implements PatchElement {

    private final String id;
    private String description = "no description";
    private PatchElementProvider provider;
    private final List<ContentModification> modifications = new ArrayList<ContentModification>();

    public PatchElementImpl(String id) {
        assert id != null;
        if (!Patch.PATCH_NAME_PATTERN.matcher(id).matches()) {
            throw PatchLogger.ROOT_LOGGER.illegalPatchName(id);
        }
        this.id = id;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.patching.metadata.PatchElement#getId()
     */
    @Override
    public String getId() {
        return id;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.patching.metadata.PatchElement#getDescription()
     */
    @Override
    public String getDescription() {
        return description;
    }

    public PatchElementImpl setDescription(String descr) {
        this.description = descr;
        return this;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.patching.metadata.PatchElement#getProvider()
     */
    @Override
    public PatchElementProvider getProvider() {
        return provider;
    }

    public PatchElementImpl setProvider(PatchElementProvider provider) {
        assert provider != null;
        this.provider = provider;
        return this;
    }

    @Override
    public List<ContentModification> getModifications() {
        return modifications;
    }

    public PatchElementImpl addContentModification(ContentModification modification) {
        this.modifications.add(modification);
        return this;
    }

}
