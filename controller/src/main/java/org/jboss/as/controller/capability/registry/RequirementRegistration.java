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
 * Encapsulates the registration information for a requirement for a capability.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RequirementRegistration {

    private final String requiredName;
    private final CapabilityId dependentId;

    public RequirementRegistration(String requiredName, String dependentName, CapabilityContext dependentContext) {
        this(requiredName, new CapabilityId(dependentName, dependentContext));
    }

    protected RequirementRegistration(String requiredName, CapabilityId dependentId) {
        this.requiredName = requiredName;
        this.dependentId = dependentId;
    }

    public String getRequiredName() {
        return requiredName;
    }

    public String getDependentName() {
        return dependentId.getName();
    }

    public CapabilityContext getDependentContext() {
        return dependentId.getContext();
    }

    public CapabilityId getDependentId() {
        return dependentId;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !getClass().isAssignableFrom(o.getClass())) return false;

        RequirementRegistration that = (RequirementRegistration) o;

        return dependentId.equals(that.dependentId)
                && requiredName.equals(that.requiredName);

    }

    @Override
    public final int hashCode() {
        int result = requiredName.hashCode();
        result = 31 * result + dependentId.hashCode();
        return result;
    }
}
