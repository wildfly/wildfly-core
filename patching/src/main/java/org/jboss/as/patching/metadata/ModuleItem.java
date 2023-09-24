/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

/**
 * @author Emanuel Muckenhuber
 */
public class ModuleItem extends ContentItem {

    public static final String MAIN_SLOT = "main";

    private final String slot;

    public ModuleItem(String name, String slot, byte[] contentHash) {
        this(name, slot, contentHash, ContentType.MODULE);
    }

    ModuleItem(String name, String slot, final byte[] contentHash, ContentType type) {
        super(name, contentHash, type);
        this.slot = slot == null ? MAIN_SLOT : slot;
    }

    /**
     * Get the module slot.
     *
     * @return the module slot
     */
    public String getSlot() {
        return slot;
    }

    @Override
    public String getRelativePath() {
        return getName() + ":" + slot;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((slot == null) ? 0 : slot.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ModuleItem other = (ModuleItem) obj;
        if (slot == null) {
            if (other.slot != null)
                return false;
        } else if (!slot.equals(other.slot))
            return false;
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(ModuleItem.class.getSimpleName()).append("{");
        builder.append(getName()).append(":").append(slot).append("}");
        return builder.toString();
    }
}
