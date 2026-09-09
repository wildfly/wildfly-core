/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchElementProvider;


/**
 * @author Alexey Loubyansky
 *
 */
public class PatchElementProviderImpl implements PatchElementProvider, RequiresCallback, IncompatibleWithCallback {

    private final String name;
    private final boolean isAddOn;
    private Patch.PatchType patchType;
    private Collection<String> incompatibleWith = Collections.emptyList();
    private Collection<String> requires = Collections.emptyList();

    public PatchElementProviderImpl(String name, boolean isAddOn) {
        assert name != null;
        this.name = name;
        this.isAddOn = isAddOn;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Patch.PatchType getPatchType() {
        return patchType;
    }

    @Override
    public Collection<String> getRequires() {
        return requires;
    }

    @Override
    public boolean isAddOn() {
        return isAddOn;
    }

    public void upgrade() {
        this.patchType = Patch.PatchType.CUMULATIVE;
    }

    public void oneOffPatch() {
        this.patchType = Patch.PatchType.ONE_OFF;
    }

    @Override
    public PatchElementProviderImpl incompatibleWith(final String patchID) {
        assert patchID != null;
        if (incompatibleWith.isEmpty()) {
            incompatibleWith = new ArrayList<String>();
        }
        incompatibleWith.add(patchID);
        return this;
    }

    @Override
    public PatchElementProviderImpl require(String elementId) {
        if(requires.isEmpty()) {
            requires = new ArrayList<String>();
        }
        requires.add(elementId);
        return this;
    }

}
