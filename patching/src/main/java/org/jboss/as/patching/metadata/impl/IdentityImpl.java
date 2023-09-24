/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.jboss.as.patching.metadata.Identity;
import org.jboss.as.patching.metadata.Patch;


/**
 * @author Alexey Loubyansky
 *
 */
public class IdentityImpl implements Identity, RequiresCallback, IncompatibleWithCallback, Identity.IdentityUpgrade {

    private final String name;
    private final String version;
    private String resultingVersion;
    private Patch.PatchType patchType;
    private Collection<String> incompatibleWith = Collections.emptyList();
    private Collection<String> requires = Collections.emptyList();

    public IdentityImpl(String name, String version) {
        assert name != null;
        assert version != null;
        this.name = name;
        this.version = version;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public Patch.PatchType getPatchType() {
        return patchType;
    }

    @Override
    public String getResultingVersion() {
        return resultingVersion;
    }

    @Override
    public Collection<String> getRequires() {
        return requires;
    }

    @Override
    public Collection<String> getIncompatibleWith() {
        return incompatibleWith;
    }

    @Override
    public IdentityImpl require(String patchId) {
        assert patchId != null;
        if(requires.isEmpty()) {
            requires = new ArrayList<String>();
        }
        requires.add(patchId);
        return this;
    }

    @Override
    public IncompatibleWithCallback incompatibleWith(String patchID) {
        assert patchID != null;
        if (incompatibleWith.isEmpty()) {
            incompatibleWith = new ArrayList<String>();
        }
        incompatibleWith.add(patchID);
        return this;
    }

    public void setResultingVersion(String resultingVersion) {
        this.resultingVersion = resultingVersion;
    }

    public void setPatchType(Patch.PatchType patchType) {
        this.patchType = patchType;
    }

    @Override
    public <T extends Identity> T forType(Patch.PatchType patchType, Class<T> clazz) {
        assert patchType == this.patchType;
        if (patchType == Patch.PatchType.CUMULATIVE) {
            assert resultingVersion != null;
        }
        return clazz.cast(this);
    }

}
