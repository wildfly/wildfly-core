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

import org.jboss.as.controller.PathAddress;

/**
 * Registry of {@link org.jboss.as.controller.capability.RuntimeCapability capabilities} available in the runtime.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface RuntimeCapabilityRegistry extends ImmutableCapabilityRegistry {
    /**
     * Registers a capability with the system. Any
     * {@link org.jboss.as.controller.capability.AbstractCapability#getRequirements() requirements}
     * associated with the capability will be recorded as requirements.
     *
     * @param capability the capability. Cannot be {@code null}
     */
    void registerCapability(RuntimeCapabilityRegistration capability);

    /**
     * Registers an additional requirement a capability has beyond what it was aware of when {@code capability}
     * was passed to {@link #registerCapability(RuntimeCapabilityRegistration)}. Used for cases
     * where a capability optionally depends on another capability, and whether or not that requirement is needed is
     * not known when the capability is first registered.
     *
     * @param requirement the requirement
     * @throws java.lang.IllegalArgumentException if no matching capability is currently
     *                                            {@link #registerCapability(RuntimeCapabilityRegistration) registered} for either {@code required} or {@code dependent}
     */
    void registerAdditionalCapabilityRequirement(RuntimeRequirementRegistration requirement);

    /**
     * Remove a previously registered requirement for a capability.
     *
     * @param requirement the requirement. Cannot be {@code null}
     * @see #registerAdditionalCapabilityRequirement(org.jboss.as.controller.capability.registry.RuntimeRequirementRegistration)
     */
    void removeCapabilityRequirement(RuntimeRequirementRegistration requirement);

    /**
     * Remove a previously registered capability if all registration points for it have been removed.
     *
     * @param capabilityName    the name of the capability. Cannot be {@code null}
     * @param context           the context in which the capability is registered. Cannot be {@code null}
     * @param registrationPoint the specific registration point that is being removed
     * @return the capability that was removed, or {@code null} if no matching capability was registered or other
     * registration points for the capability still exist
     */
    RuntimeCapabilityRegistration removeCapability(String capabilityName, CapabilityContext context, PathAddress registrationPoint);

}
