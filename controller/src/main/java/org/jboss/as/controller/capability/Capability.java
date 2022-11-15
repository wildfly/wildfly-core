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

package org.jboss.as.controller.capability;

import java.util.Set;

import org.jboss.as.controller.PathAddress;

/**
 * Basic description of a capability.
 *
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface Capability {

    /**
     * Gets the basic name of the capability. If {@link #isDynamicallyNamed()} returns {@code true}
     * this will be the basic name of the capability, not including any dynamic portions.
     *
     * @return the name. Will not be {@code null}
     *
     * @see #getDynamicName(String)
     */
    String getName();

    /**
     * Gets the names of other capabilities required by this capability.
     * These are static requirements.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     */
    Set<String> getRequirements();

    /**
     * Gets whether this capability is a dynamically named one, whose runtime variants
     * will have a dynamic element added to the base name provided by {@link #getName()}.
     *
     * @return {@code true} if this capability is dynamically named
     */
    boolean isDynamicallyNamed();

    /**
     * Gets the full name of a capability, including a dynamic element
     * @param dynamicNameElement the dynamic portion of the name. Cannot be {@code null}
     * @return the full capability name
     *
     * @throws IllegalStateException if {@link #isDynamicallyNamed()} returns {@code false}
     */
    String getDynamicName(String dynamicNameElement);

    String getDynamicName(PathAddress address);
}
