/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

/**
 * Information about a deployed extension
 *
 * @author Stuart Douglas
 *
 */
public class ExtensionInfo {
    private final String name;
    private final String specVersion;
    private final String implVersion;
    private final String implVendorId;

    public ExtensionInfo(String name, String specVersion, String implVersion, String implVendorId) {
        this.name = name;
        this.specVersion = specVersion;
        this.implVersion = implVersion;
        this.implVendorId = implVendorId;
    }

    public String getName() {
        return name;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public String getImplVersion() {
        return implVersion;
    }

    public String getImplVendorId() {
        return implVendorId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((implVendorId == null) ? 0 : implVendorId.hashCode());
        result = prime * result + ((implVersion == null) ? 0 : implVersion.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((specVersion == null) ? 0 : specVersion.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ExtensionInfo other = (ExtensionInfo) obj;
        if (implVendorId == null) {
            if (other.implVendorId != null)
                return false;
        } else if (!implVendorId.equals(other.implVendorId))
            return false;
        if (implVersion == null) {
            if (other.implVersion != null)
                return false;
        } else if (!implVersion.equals(other.implVersion))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (specVersion == null) {
            if (other.specVersion != null)
                return false;
        } else if (!specVersion.equals(other.specVersion))
            return false;
        return true;
    }

}
