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
 * Unique identifier for a capability, encapsulating its name and the context in which it exists.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class CapabilityId {

    private final String name;
    private final CapabilityContext context;
    private final int hash;

    public CapabilityId(String name, CapabilityContext context) {
        this.name = name;
        this.context = context;

        int theHash = name.hashCode();
        theHash = 31 * theHash + context.hashCode();
        hash = theHash;
    }

    /**
     * Gets the name of the capability. Must be unique within the given context, so providers of capabilities
     * should use a distinct namespace as part of the name. The {@code org.wildfly} namespace and any child namespaces
     * are reserved for use by the WildFly project or its component projects itself.
     *
     * @return the name. Will not be {@code null}
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the context in which the capability exists. A single management process may handle multiple contexts
     * simultaneously, and a given capability may not exist in all of them. An example are the various profiles
     * in a managed domain, some of which may have a capability registered and other may not.
     *
     * @return the context. Will not be {@code null}
     */
    public CapabilityContext getContext() {
        return context;
    }

    /**
     * Gets whether a capability with this id can satisfy a requirement represented by another id.
     *
     * @param other the other id. Cannot be {@code null}
     * @return {@code true} if this id has the same name as the other id, and if this id's context
     *           {@link org.jboss.as.controller.capability.registry.CapabilityContext#canSatisfyRequirements(CapabilityContext) can satisfy requirements}
     *           for the other id's context
     */
    public boolean canSatisfyRequirements(CapabilityId other) {
        return name.equals(other.name) && context.canSatisfyRequirements(other.getContext());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CapabilityId that = (CapabilityId) o;

        return name.equals(that.name) && context.equals(that.context);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return context.getName() + "/" + name;
    }
}
