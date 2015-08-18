/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.capability.registry;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.Capability;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface PossibleCapabilityRegistry {

    /**
     * Registers a capability with the system. Any
     * {@link org.jboss.as.controller.capability.AbstractCapability#getRequirements() requirements}
     * associated with the capability will be recorded as requirements.
     *
     * @param capability the capability. Cannot be {@code null}
     */

    void registerPossibleCapability(Capability capability, PathAddress registrationPoint);


    /**
     * Remove a previously registered capability if all registration points for it have been removed.
     *
     * @param capabilityName    the name of the capability. Cannot be {@code null}
     * @param context           the context in which the capability is registered. Cannot be {@code null}
     * @param registrationPoint the specific registration point that is being removed
     * @return the capability that was removed, or {@code null} if no matching capability was registered or other
     * registration points for the capability still exist
     */
    CapabilityRegistration removePossibleCapability(String capabilityName, CapabilityContext context, PathAddress registrationPoint);

}
