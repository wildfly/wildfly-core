/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * @author Emanuel Muckenhuber
 */
class Location {

    private final ContentItem item;
    private final int hashCode;

    Location(ContentItem item) {
        this.item = item;
        this.hashCode = hashCode(item);
    }

    public ContentItem getItem() {
        return item;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Location location = (Location) o;
        //
        return hashCode == location.hashCode;
    }

    static int hashCode(final ContentItem item) {
        final ContentType type = item.getContentType();
        switch (type) {
            case MODULE:
            case BUNDLE:
                final ModuleItem module = (ModuleItem) item;
                final String[] path = module.getName().split("\\.");
                return hashCode(type.toString(), module.getSlot(), path);
            case MISC:
                final MiscContentItem misc = (MiscContentItem) item;
                return hashCode(type.toString(), misc.getName(), misc.getPath());
            default:
                throw new IllegalStateException();
        }
    }

    static int hashCode(final String root, final String name, final String... path) {
        int hash = root.hashCode();
        for(final String p : path) {
            hash = 31 * hash + p.hashCode();
        }
        hash = 31 * hash + name.hashCode();
        return hash;
    }

}
