/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.repository;

import java.util.Objects;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ContentReference {
    /**
     * Content that is not at least 5 minutes old won't be cleaned.
     */
    private static final long CLEANABLE_TIME = 300000L;
    private final String contentIdentifier;
    private final String hexHash;
    private final long timestamp;

    public ContentReference(String contentIdentifier, String hexHash) {
        this(contentIdentifier, hexHash, System.currentTimeMillis());
    }

    public ContentReference(String contentIdentifier, String hexHash, long timestamp) {
        this.contentIdentifier = contentIdentifier;
        this.hexHash = hexHash;
        this.timestamp = timestamp;
    }

    public ContentReference(String contentIdentifier, byte[] hash) {
        this(contentIdentifier, hash, System.currentTimeMillis());
    }

    public ContentReference(String contentIdentifier, byte[] hash, long timestamp) {
        this.contentIdentifier = contentIdentifier;
        this.hexHash = HashUtil.bytesToHexString(hash);
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getContentIdentifier() {
        return contentIdentifier;
    }

    public String getHexHash() {
        return hexHash;
    }

    public byte[] getHash() {
        return HashUtil.hexStringToByteArray(hexHash);
    }

    public boolean mayBeCleaned() {
        return System.currentTimeMillis() - timestamp > CLEANABLE_TIME;
    }

    @Override
    public int hashCode() {
        int hashCode = 7;
        hashCode = 43 * hashCode + Objects.hashCode(this.contentIdentifier);
        hashCode = 43 * hashCode + Objects.hashCode(this.hexHash);
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ContentReference other = (ContentReference) obj;
        if (!Objects.equals(this.contentIdentifier, other.contentIdentifier)) {
            return false;
        }
        if (!Objects.equals(this.hexHash, other.hexHash)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ContentReference{" + "contentIdentifier=" + contentIdentifier + ", hexHash=" + hexHash + ", timestamp=" + timestamp + '}';
    }

}
