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

import org.jboss.as.controller.capability.AbstractCapability;

/**
 * Encapsulates the registration information for an {@link org.jboss.as.controller.capability.AbstractCapability capability}.
 *
 * @param <C> the specific type of capability associated with this registration
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class CapabilityRegistration<C extends AbstractCapability> {

    private final C capability;
    private final CapabilityId id;

    public CapabilityRegistration(C capability, CapabilityContext context) {
        this.capability = capability;
        this.id = new CapabilityId(capability.getName(), context);

    }

    /**
     * Gets the capability.
     * @return the capability. Will not return {@code null}
     */
    public C getCapability() {
        return capability;
    }

    /**
     * Gets the context in which the capability is registered.
     *
     * @return the capability context. Will not return {@code null}
     */
    public CapabilityContext getCapabilityContext() {
        return id.getContext();
    }

    public String getCapabilityName() {
        return id.getName();
    }

    public CapabilityId getCapabilityId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CapabilityRegistration that = (CapabilityRegistration) o;

        return id.equals(that.id);

    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
