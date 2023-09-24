/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

/**
 * Unique identifier for a capability, encapsulating its name and the scope in which it exists.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class CapabilityId implements Comparable<CapabilityId> {

    private final String name;
    private final CapabilityScope scope;
    private final int hash;

    public CapabilityId(String name, CapabilityScope scope) {
        this.name = name;
        this.scope = scope;

        int theHash = name.hashCode();
        theHash = 31 * theHash + scope.hashCode();
        hash = theHash;
    }

    /**
     * Gets the name of the capability. Must be unique within the given scope, so providers of capabilities
     * should use a distinct namespace as part of the name. The {@code org.wildfly} namespace and any child namespaces
     * are reserved for use by the WildFly project or its component projects itself.
     *
     * @return the name. Will not be {@code null}
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the scope in which the capability exists. A single management process may handle multiple scopes
     * simultaneously, and a given capability may not exist in all of them. An example are the various profiles
     * in a managed domain, some of which may have a capability registered and other may not.
     *
     * @return the context. Will not be {@code null}
     */
    public CapabilityScope getScope() {
        return scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CapabilityId that = (CapabilityId) o;

        return name.equals(that.name) && scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return scope.getName() + "/" + name;
    }

    @Override
    public int compareTo(CapabilityId o) {
        if (equals(o)) {
            return 0;
        }
        int result = scope.getName().compareTo(o.scope.getName());
        return result != 0 ? result : name.compareTo(o.name);
    }
}
