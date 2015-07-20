/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

/**
 * {@link CapabilityContext} for domain-scoped resources (i.e. profiles and socket binding groups).
 *
 * @author Brian Stansberry
 */
public final class DomainCapabilityContext implements CapabilityContext {

    private final String type;
    private final boolean socketBinding;
    private final String value;
    private final boolean requiresConsistencyCheck;

    public DomainCapabilityContext(boolean socketBinding, String value, boolean requiresConsistencyCheck) {
        this.socketBinding = socketBinding;
        this.type = socketBinding ? SOCKET_BINDING_GROUP : PROFILE;
        this.value = value;
        this.requiresConsistencyCheck = requiresConsistencyCheck;
    }

    @Override
    public boolean canSatisfyRequirements(CapabilityContext dependentContext) {
        // Currently this is a simple match of type and value, but once profile/socket-binding-group
        // includes are once again supported we need to account for those
        return equals(dependentContext) ||
                (socketBinding && (!(dependentContext instanceof DomainCapabilityContext)
                        || !((DomainCapabilityContext) dependentContext).socketBinding));
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return requiresConsistencyCheck;
    }

    @Override
    public String getName() {
        return type + "=" + value;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getName() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DomainCapabilityContext that = (DomainCapabilityContext) o;

        return type.equals(that.type) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }
}
