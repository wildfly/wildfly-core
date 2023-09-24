/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.Patch.PatchType;
import org.jboss.as.patching.metadata.impl.IdentityImpl;
import org.jboss.as.patching.metadata.impl.PatchElementProviderImpl;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchBuilder extends ModificationBuilderTarget<PatchBuilder> implements Builder, PatchMetadataResolver {

    protected String patchId;
    private String description;
    private String link;
    private Identity identity;
    private PatchType patchType;
    private final List<ContentModification> modifications = new ArrayList<ContentModification>();
    private List<PatchElementHolder> elements = Collections.emptyList();
    private Set<String> elementIds = Collections.emptySet();

    public static PatchBuilder create() {
        return new PatchBuilder();
    }

    protected PatchBuilder() {
    }

    public PatchBuilder setPatchId(String patchId) {
        if (!Patch.PATCH_NAME_PATTERN.matcher(patchId).matches()) {
            throw PatchLogger.ROOT_LOGGER.illegalPatchName(patchId);
        }
        this.patchId = patchId;
        return this;
    }

    public PatchBuilder setLink(String link) {
        this.link = link;
        return this;
    }

    public PatchBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public PatchIdentityBuilder upgradeIdentity(final String name, final String version, final String resultingVersion) {
        final PatchIdentityBuilder builder = new PatchIdentityBuilder(name, version, PatchType.CUMULATIVE, this);
        final IdentityImpl identity = builder.getIdentity();
        identity.setResultingVersion(resultingVersion);
        this.identity = identity;
        this.patchType = PatchType.CUMULATIVE;
        return builder;
    }

    public PatchIdentityBuilder oneOffPatchIdentity(final String name, final String version) {
        final PatchIdentityBuilder builder = new PatchIdentityBuilder(name, version, PatchType.ONE_OFF, this);
        final IdentityImpl identity = builder.getIdentity();
        this.identity = identity;
        this.patchType = PatchType.ONE_OFF;
        return builder;
    }

    @Override
    protected PatchBuilder internalAddModification(ContentModification modification) {
        this.modifications.add(modification);
        return this;
    }

    public PatchElementBuilder upgradeElement(final String patchId, final String layerName, final boolean addOn) {
        if (!Patch.PATCH_NAME_PATTERN.matcher(patchId).matches()) {
            throw PatchLogger.ROOT_LOGGER.illegalPatchName(patchId);
        }
        final PatchElementBuilder builder = new PatchElementBuilder(patchId, layerName, addOn, this);
        builder.upgrade();
        addElement(patchId, builder);
        return builder;
    }

    public PatchElementBuilder oneOffPatchElement(final String patchId, final String layerName, final boolean addOn) {
        if (!Patch.PATCH_NAME_PATTERN.matcher(patchId).matches()) {
            throw PatchLogger.ROOT_LOGGER.illegalPatchName(patchId);
        }
        final PatchElementBuilder builder = new PatchElementBuilder(patchId, layerName, addOn, this);
        builder.oneOffPatch();
        addElement(patchId, builder);
        return builder;
    }

    public PatchElementBuilder addElement(final String patchId, final String layerName, final boolean addOn) {
        if (!Patch.PATCH_NAME_PATTERN.matcher(patchId).matches()) {
            throw PatchLogger.ROOT_LOGGER.illegalPatchName(patchId);
        }
        final PatchElementBuilder builder = new PatchElementBuilder(patchId, layerName, addOn, this);
        //builder.cumulativePatch();
        addElement(patchId, builder);
        return builder;
    }

    public PatchBuilder addElement(final PatchElement element) {
        addElement(element.getId(), new PatchElementHolder() {
            @Override
            public PatchElement createElement(PatchType patchType) {
                final PatchType type = element.getProvider().getPatchType();
                if (type == null) {
                    if (patchType == PatchType.CUMULATIVE) {
                        ((PatchElementProviderImpl)element.getProvider()).upgrade();
                    } else {
                        ((PatchElementProviderImpl)element.getProvider()).oneOffPatch();
                    }
                } else if (patchType != PatchBuilder.this.patchType) {
                    throw PatchLogger.ROOT_LOGGER.patchTypesDontMatch();
                }
                return element;
            }
        });
        return this;
    }

    protected void addElement(String id, PatchElementHolder element) {
        switch(elements.size()) {
            case 0:
                elements = Collections.singletonList(element);
                elementIds = Collections.singleton(id);
                break;
            case 1:
                elements = new ArrayList<PatchElementHolder>(elements);
                elementIds = new HashSet<String>(elementIds);
            default:
                elements.add(element);
                if(!id.equals(Constants.BASE) && !elementIds.add(id)) {
                    throw PatchLogger.ROOT_LOGGER.duplicateElementPatchId(id);
                }
        }
    }

    public List<ContentModification> getModifications() {
        return modifications;
    }

    @Override
    public Patch resolvePatch(String name, String version) throws PatchingException {
        return build();
    }

    @Override
    public Patch build() {
        assert notNull(identity);
        assert notNull(patchId);

        // Create the elements
        final List<PatchElement> elements = new ArrayList<PatchElement>();
        for (final PatchElementHolder holder : this.elements) {
            elements.add(holder.createElement(patchType));
        }

        return new PatchImpl(patchId, description, link, identity, unmodifiableList(elements), unmodifiableList(modifications));
    }

    @Override
    protected PatchBuilder returnThis() {
        return this;
    }

    static boolean notNull(Object o) {
        return o != null;
    }

    protected interface PatchElementHolder {

        PatchElement createElement(final PatchType type);

    }

}
