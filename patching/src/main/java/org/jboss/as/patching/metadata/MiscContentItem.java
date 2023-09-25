/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import java.util.Arrays;
import java.util.List;

/**
 * @author Emanuel Muckenhuber
 */
public class MiscContentItem extends ContentItem {

    private final String[] path;
    private final boolean isDirectory;
    private final boolean affectsRuntime;

    public MiscContentItem(String name, List<String> path, byte[] contentHash, boolean directory) {
        this(name, path.toArray(new String[path.size()]), contentHash, directory, false);
    }

    public MiscContentItem(String name, String[] path, byte[] contentHash) {
        this(name, path, contentHash, false, false);
    }

    public MiscContentItem(String name, String[] path, byte[] contentHash, boolean isDirectory, boolean affectsRuntime) {
        super(name, contentHash, ContentType.MISC);
        this.path = path;
        this.isDirectory = isDirectory;
        this.affectsRuntime = affectsRuntime;
    }

    /**
     * Whether the item is a directory or not
     *
     * @return Whether the item is a directory or not
     */
    public boolean isDirectory() {
        return isDirectory;
    }

    /**
     * Affects the runtime directly.
     *
     * @return {@code true} if it directly affects the runtime, {@code false otherwise}
     */
    public boolean isAffectsRuntime() {
        return affectsRuntime;
    }

    /**
     * Get the relative content path.
     *
     * @return the content path
     */
    public String[] getPath() {
        return path;
    }

    /**
     * Get the relative path.
     *
     * @return the relative path
     */
    public String getRelativePath() {
        final StringBuilder builder = new StringBuilder();
        for(final String p : path) {
            builder.append(p).append("/");
        }
        builder.append(getName());
        return builder.toString();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(MiscContentItem.class.getSimpleName()).append("{");
        for(final String path : getPath()) {
            builder.append(path).append("/");
        }
        builder.append(getName()).append("}");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (affectsRuntime ? 1231 : 1237);
        result = prime * result + (isDirectory ? 1231 : 1237);
        result = prime * result + Arrays.hashCode(path);
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
        MiscContentItem other = (MiscContentItem) obj;
        if (affectsRuntime != other.affectsRuntime)
            return false;
        if (isDirectory != other.isDirectory)
            return false;
        return Arrays.equals(path, other.path);
    }
}
