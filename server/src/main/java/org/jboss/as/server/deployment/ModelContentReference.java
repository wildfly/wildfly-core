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
package org.jboss.as.server.deployment;

import java.util.Objects;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.repository.ContentReference;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ModelContentReference {

    private static final long CLEANABLE_TIME = 10000L;//300000L;
    private PathAddress address;
    private String hash;
    private String hashAttributeName;
    private String contentAttributeName;
    private long timestamp;

    ModelContentReference(PathAddress address, String hash) {
        this(address, hash, CONTENT, HASH, System.currentTimeMillis());
    }

    ModelContentReference(PathAddress address, String hash, String contentAttributeName, String hashAttributeName, long timestamp) {
        assert address != null;
        assert address.toCLIStyleString().contains("=");
        assert address.toCLIStyleString().contains("/");
        if(address == null || !address.toCLIStyleString().contains("=") || !address.toCLIStyleString().contains("/")) {
            throw new IllegalArgumentException("WTF is this address " + address);
        }
        this.hash = hash;
        this.address = address;
        this.hashAttributeName = hashAttributeName;
        this.contentAttributeName = contentAttributeName;
        this.timestamp = timestamp;
    }

    public String getHexHash() {
        return hash;
    }

    public byte[] getHash() {
        return HashUtil.hexStringToByteArray(hash);
    }

    public PathAddress getContentIdentifier() {
        return address;
    }

    public String getContentKey() {
        return contentAttributeName;
    }

    public String getHashKey() {
        return hashAttributeName;
    }

    /**
     * For testing purpose only
     * @return
     */
    long getTimestamp() {
        return timestamp;
    }

    public ContentReference toReference() {
        return new ContentReference(getContentIdentifier().toCLIStyleString()+ '@' + getContentKey() + '/' + getHashKey(), hash, timestamp);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.address);
        hash = 41 * hash + Objects.hashCode(this.hash);
        hash = 41 * hash + Objects.hashCode(this.hashAttributeName);
        hash = 41 * hash + Objects.hashCode(this.contentAttributeName);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ModelContentReference other = (ModelContentReference) obj;
        if (!Objects.equals(this.address, other.address)) {
            return false;
        }
        if (!Objects.equals(this.hash, other.hash)) {
            return false;
        }
        if (!Objects.equals(this.hashAttributeName, other.hashAttributeName)) {
            return false;
        }
        if (!Objects.equals(this.contentAttributeName, other.contentAttributeName)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ModelContentReference{" + "address=" + address + ", hash=" + hash + ", hashAttributeName=" + hashAttributeName + ", contentAttributeName=" + contentAttributeName + ", timestamp=" + timestamp + '}';
    }

    public static final ModelContentReference fromDeploymentName(String name, String hash) {
        return new ModelContentReference(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT, name)), hash);
    }

    public static final ModelContentReference fromDeploymentName(String name, byte[] hash) {
        return fromDeploymentName(name, HashUtil.bytesToHexString(hash));
    }

    public static final ModelContentReference fromDeploymentAddress(PathAddress address, byte[] hash) {
        return new ModelContentReference(address, HashUtil.bytesToHexString(hash), CONTENT, HASH , System.currentTimeMillis());
    }
}
