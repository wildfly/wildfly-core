/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import static org.jboss.as.patching.metadata.ContentType.BUNDLE;

/**
 * {@see ModuleIdentityRepository}
 *
 * @author Emanuel Muckenhuber
 */
public class BundleItem extends ModuleItem {


    public BundleItem(String name, String slot, byte[] contentHash) {
        super(name, slot, contentHash, BUNDLE);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(BundleItem.class.getSimpleName()).append("{");
        builder.append(getName()).append(":").append(getSlot()).append("}");
        return builder.toString();
    }
}
