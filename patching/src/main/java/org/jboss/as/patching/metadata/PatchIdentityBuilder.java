/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import org.jboss.as.patching.metadata.impl.IdentityImpl;
import org.jboss.as.patching.metadata.impl.IncompatibleWithCallback;
import org.jboss.as.patching.metadata.impl.RequiresCallback;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchIdentityBuilder implements RequiresCallback, IncompatibleWithCallback {

    private final PatchBuilder parent;
    private final IdentityImpl identity;

    public PatchIdentityBuilder(final String name, final String version, final Patch.PatchType patchType, final PatchBuilder parent) {
        this.identity = new IdentityImpl(name, version);
        this.identity.setPatchType(patchType);
        this.parent = parent;
    }

    IdentityImpl getIdentity() {
        return identity;
    }

    @Override
    public PatchIdentityBuilder incompatibleWith(String patchID) {
        identity.incompatibleWith(patchID);
        return this;
    }

    @Override
    public PatchIdentityBuilder require(String id) {
        identity.require(id);
        return this;
    }

    public PatchBuilder getParent() {
        return parent;
    }

}
