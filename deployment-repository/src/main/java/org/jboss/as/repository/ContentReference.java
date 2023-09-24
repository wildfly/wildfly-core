/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import java.util.Objects;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ContentReference {

    private final String contentIdentifier;
    private final String hexHash;

    public ContentReference(String contentIdentifier, String hexHash) {
        this.contentIdentifier = contentIdentifier;
        if (hexHash == null) {
            this.hexHash = "";
        } else {
            this.hexHash = hexHash;
        }
    }

    public ContentReference(String contentIdentifier, byte[] hash) {
        this(contentIdentifier, hash, System.currentTimeMillis());
    }

    public ContentReference(String contentIdentifier, byte[] hash, long timestamp) {
        this.contentIdentifier = contentIdentifier;
        if (hash == null || hash.length == 0) {
            this.hexHash = "";
        } else {
            this.hexHash = HashUtil.bytesToHexString(hash);
        }
    }

    public String getContentIdentifier() {
        return contentIdentifier;
    }

    public String getHexHash() {
        return hexHash;
    }

    public byte[] getHash() {
        if(hexHash.isEmpty()) {
            return new byte[0];
        }
        return HashUtil.hexStringToByteArray(hexHash);
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
        return "ContentReference{" + "contentIdentifier=" + contentIdentifier + ", hexHash=" + hexHash + '}';
    }

}
