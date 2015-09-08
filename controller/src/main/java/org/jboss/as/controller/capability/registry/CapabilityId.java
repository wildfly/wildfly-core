/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
