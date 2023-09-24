/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import java.util.Objects;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ContentRepositoryElement {

    private final String path;
    private final boolean folder;
    private final long size;

    private ContentRepositoryElement(String path, boolean folder, long size) {
        this.path = path;
        this.folder = folder;
        this.size = size;
    }

    public static ContentRepositoryElement createFolder(String path) {
        return new ContentRepositoryElement(path, true, -1);
    }

    public static ContentRepositoryElement createFile(String path, long size) {
        return new ContentRepositoryElement(path, false, size);
    }

    /**
     * Get the value of size
     *
     * @return the value of size
     */
    public long getSize() {
        return size;
    }
    /**
     * Get the value of folder
     *
     * @return the value of folder
     */
    public boolean isFolder() {
        return folder;
    }

    /**
     * Get the value of path
     *
     * @return the value of path
     */
    public String getPath() {
        return path;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.path);
        hash = 97 * hash + (this.folder ? 1 : 0);
        hash = 97 * hash + (int) (this.size ^ (this.size >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ContentRepositoryElement other = (ContentRepositoryElement) obj;
        if (this.folder != other.folder) {
            return false;
        }
        if (this.size != other.size) {
            return false;
        }
        if (!Objects.equals(this.path, other.path)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ContentRepositoryElement{" + "path=" + path + ", folder=" + folder + ", size=" + size + '}';
    }
}
