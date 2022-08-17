/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
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

package org.jboss.as.controller;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.ServiceTarget;

/**
 * The target of ServiceBuilder for capability installations.
 * CapabilityServiceBuilder to be installed on a target should be retrieved by calling one of the {@code addCapability} methods.
 * Notice that installation will only take place after {@link CapabilityServiceBuilder#install()} is invoked.
 * CapabilityServiceBuilder that are not installed are ignored.
 *
 * @author Tomaz Cerar (c) 2017 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface CapabilityServiceTarget extends ServiceTarget {

    /**
     * Gets a builder which can be used to add a capability service into this capability target.
     *
     * @param capability the capability to be installed
     * @return new capability builder instance
     * @throws IllegalArgumentException if capability does not provide a service
     */
    CapabilityServiceBuilder<?> addCapability(final RuntimeCapability<?> capability) throws IllegalArgumentException;

}
