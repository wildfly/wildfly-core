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

/**
 * {@link CapabilityContext} specifically used for the {@code org.wildfly.domain.profile} capability.
 * <p>
 * <strong>NOTE:</strong> This context is not used for child resources (subsystems) in the 'profile'
 * part of the Host Controller resource tree.
 *
 * @author Brian Stansberry
 */
class ProfilesCapabilityContext implements CapabilityContext {

    public static final ProfilesCapabilityContext INSTANCE = new ProfilesCapabilityContext();

    @Override
    public boolean canSatisfyRequirement(String requiredName, CapabilityContext dependentContext, CapabilityResolutionContext context) {
        return dependentContext instanceof ProfilesCapabilityContext || dependentContext instanceof ServerGroupsCapabilityContext;
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return false;
    }

    @Override
    public String getName() {
        return "profiles";
    }
}
