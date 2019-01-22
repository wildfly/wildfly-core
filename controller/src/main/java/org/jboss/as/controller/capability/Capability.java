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
     * Gets the names of other capabilities optionally required by this capability.
     * Whether this capability will actually require the other capabilities is not
     * statically known but rather depends on this capability's own configuration.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     *
     * @deprecated Never returns anything but an empty set and likely will be removed in the next release.
     */
    @Deprecated
    Set<String> getOptionalRequirements();

    /**
     * Gets the names of other capabilities optionally used by this capability if they
     * are present in the runtime, but where the use of the other capability will never
     * be mandated by the persistent configuration. Differs from
     * {@link #getOptionalRequirements() optional requirements}
     * in that optional requirements may or may not be specified by the persistent
     * configuration, but if they are the capability must be present or the configuration
     * is invalid.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     *
     * @deprecated Never returns anything but an empty set and likely will be removed in the next release.
     */
    @Deprecated
    Set<String> getRuntimeOnlyRequirements();

    /**
     * Gets the names of other dynamically named capabilities upon a concrete instance of which this
     * capability will have a hard requirement once the full name is known. It is statically
     * known that some variant of these base capability names will be required, but the
     * exact name will not be known until this capability's configuration is read.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     *
     * @deprecated Never returns anything but an empty set and likely will be removed in the next release.
     */
    @Deprecated
    Set<String> getDynamicRequirements();

    /**
     * Gets the names of other dynamically named capabilities upon a concrete instance of which this
     * capability will have an optional requirement once the full name is known.
     * Whether this capability will actually require the other capabilities is not
     * statically known but rather depends on this capability's own configuration.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     *
     * @deprecated Never returns anything but an empty set and likely will be removed in the next release.
     */
    @Deprecated
    Set<String> getDynamicOptionalRequirements();

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

    /**
     * Gets the names of any "additional" Galleon packages that must be installed in order
     * for this capability to function. The purpose of providing this information is to
     * make it available to the Galleon tooling that produces Galleon feature-specs,
     * in order to allow the tooling to include the package information in the relevant
     * spec.
     * <p>
     * A package is "additional" if it is not one of the "standard" packages that must be
     * installed. The names of "standard" packages should not be returned. The "standard"
     * packages are:
     *
     *  <ol>
     *      <li>
     *          The root package for the process type; i.e. the package that provides
     *          the main module whose name is passed to JBoss Modules when the process
     *          is launched.
     *      </li>
     *      <li>
     *          If this capability is provided by an {@code Extension}, the package
     *          that installs the module that provides the extension.
     *      </li>
     *      <li>
     *          Any package that is listed as an additional required package by capability
     *          upon which this capability has a {@link #getRequirements() requirement}.
     *      </li>
     *      <li>
     *          Any package that is non-optionally required, either directly or transitively,
     *          by one of the other types of standard packages.
     *      </li>
     *  </ol>
     *
     * @return the additional package names. Will not return {@code null} but may be empty
     *
     * @deprecated Use {@link org.jboss.as.controller.registry.ImmutableManagementResourceRegistration#getAdditionalRuntimePackages()}
     */
    @Deprecated
    Set<String> getAdditionalRequiredPackages();
}
