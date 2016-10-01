/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
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
 * Registry that holds possible capabilities. <p>
 * Possible capabilities are definitions of capabilities that are registered on resource to provide information
 * of what all real or runtime capabilities will be registered once instance of said resource is added to resource tree.
 *
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface PossibleCapabilityRegistry {

    /**
     * Registers a possible capability with the system.
     *
     * @param capability the possible capability. Cannot be {@code null}
     */

    void registerPossibleCapability(Capability capability, PathAddress registrationPoint);


    /**
     * Remove a previously registered possible capability if all registration points for it have been removed.
     *
     * @param capability    the capability. Cannot be {@code null}
     * @param registrationPoint the specific registration point that is being removed
     * @return the capability that was removed, or {@code null} if no matching capability was registered or other
     * registration points for the capability still exist
     */
    CapabilityRegistration<?> removePossibleCapability(Capability capability, PathAddress registrationPoint);

}
