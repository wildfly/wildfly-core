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

package org.jboss.as.controller.capability;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.jboss.as.controller.PathAddress;

/**
 * Base class for a core or subsystem capability.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public abstract class AbstractCapability implements Capability {

    private final String name;
    private final boolean dynamic;
    private final Set<String> requirements;
    private final Set<String> optionalRequirements;
    private final Set<String> runtimeOnlyRequirements;
    private final Set<String> dynamicRequirements;
    private final Set<String> dynamicOptionalRequirements;
    // note there is no field "dynamicRuntimeOnlyRequirements" as a
    // dynamic requirement requires some configuration value to
    // specify the dynamic part of the requirement name, and a
    // runtime-only requirement by definition cannot be specified
    // via configuration
    protected final Function<PathAddress,String[]> dynamicNameMapper;

    /**
     * Creates a new capability
     * @param name the name of the capability. Cannot be {@code null}
     * @param requirements names of other capabilities upon which this capability has a hard requirement. May be {@code null}
     * @param optionalRequirements names of other capabilities upon which this capability has an optional requirement. May be {@code null}
     * @param runtimeOnlyRequirements names of other capabilities upon which this capability has an optional, runtime-only requirement. May be {@code null}
     * @param dynamicRequirements names of other dynamically named capabilities upon a concrete instance of which this
*                            capability will have a hard requirement once the full name is known. May be {@code null}
     * @param dynamicOptionalRequirements names of other dynamically named capabilities upon a concrete instance of which this
*                            capability will have an optional requirement once the full name is known. May be {@code null}
     * @param dynamicNameMapper
     */
    protected AbstractCapability(final String name, final boolean dynamic,
                                 final Set<String> requirements,
                                 final Set<String> optionalRequirements,
                                 final Set<String> runtimeOnlyRequirements,
                                 final Set<String> dynamicRequirements,
                                 final Set<String> dynamicOptionalRequirements, Function<PathAddress, String[]> dynamicNameMapper) {
        assert name != null;
        this.name = name;
        this.dynamic = dynamic;
        this.requirements = establishRequirements(requirements);
        this.optionalRequirements = establishRequirements(optionalRequirements);
        this.runtimeOnlyRequirements = establishRequirements(runtimeOnlyRequirements);
        this.dynamicRequirements = establishRequirements(dynamicRequirements);
        this.dynamicOptionalRequirements = establishRequirements(dynamicOptionalRequirements);
        if (dynamicNameMapper != null) {
            this.dynamicNameMapper = dynamicNameMapper;
        } else {
            this.dynamicNameMapper = AbstractCapability::addressValueToDynamicName;
        }
    }

    private static Set<String> establishRequirements(Set<String> input) {
        if (input != null && !input.isEmpty()) {
            return Collections.unmodifiableSet(new HashSet<String>(input));
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * resolves dynamic name from path address that return last element value are result
     *
     * @param pathAddress
     * @return dynamic part of address
     */
    public static String[] addressValueToDynamicName(PathAddress pathAddress){
        return new String[]{pathAddress.getLastElement().getValue()};
    }

    /**
     * Gets the basic name of the capability. If {@link #isDynamicallyNamed()} returns {@code true}
     * this will be the basic name of the capability, not including any dynamic portions.
     *
     * @return the name. Will not be {@code null}
     *
     * @see #getDynamicName(String)
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Gets the names of other capabilities required by this capability.
     * These are static requirements.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     */
    @Override
    public Set<String> getRequirements() {
        return requirements;
    }

    /**
     * Gets the names of other capabilities optionally required by this capability.
     * Whether this capability will actually require the other capabilities is not
     * statically known but rather depends on this capability's own configuration.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     */
    @Override
    public Set<String> getOptionalRequirements() {
        return optionalRequirements;
    }

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
     */
    @Override
    public Set<String> getRuntimeOnlyRequirements() {
        return runtimeOnlyRequirements;
    }

    /**
     * Gets the names of other dynamically named capabilities upon a concrete instance of which this
     * capability will have a hard requirement once the full name is known. It is statically
     * known that some variant of these base capability names will be required, but the
     * exact name will not be known until this capability's configuration is read.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     */
    @Override
    public Set<String> getDynamicRequirements() {
        return dynamicRequirements;
    }

    /**
     * Gets the names of other dynamically named capabilities upon a concrete instance of which this
     * capability will have an optional requirement once the full name is known.
     * Whether this capability will actually require the other capabilities is not
     * statically known but rather depends on this capability's own configuration.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     */
    @Override
    public Set<String> getDynamicOptionalRequirements() {
        return dynamicOptionalRequirements;
    }

    /**
     * Gets whether this capability is a dynamically named one, whose runtime variants
     * will have a dynamic element added to the base name provided by {@link #getName()}.
     *
     * @return {@code true} if this capability is dynamically named
     */
    @Override
    public boolean isDynamicallyNamed() {
        return dynamic;
    }

    /**
     * Gets the full name of a capbility, including a dynamic element
     * @param dynamicNameElement the dynamic portion of the name. Cannot be {@code null}
     * @return the full capability name
     *
     * @throws IllegalStateException if {@link #isDynamicallyNamed()} returns {@code false}
     */
    @Override
    public String getDynamicName(String dynamicNameElement) {
        if (!dynamic) {
            throw new IllegalStateException();
        }
        return name + "." + dynamicNameElement;
    }

    @Override
    public String getDynamicName(PathAddress address) {
        if (!dynamic) {
            throw new IllegalStateException();
        }
        String[] dynamicElements = dynamicNameMapper.apply(address);
        return RuntimeCapability.buildDynamicCapabilityName(name, dynamicElements);
    }

//    /**
//     * Gets an internationalized text description of the capability.
//     *
//     * @param locale the locale to use. Cannot be {@code null}
//     * @return the text description, or {@code null} if there is no text description
//     *
//     * @throws java.lang.IllegalArgumentException if {@code locale} is {@code null}
//     */
//    public abstract String getDescription(Locale locale);

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if {@code o} is the same type as this object and its {@link #getName() name} is equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractCapability that = (AbstractCapability) o;

        return name.equals(that.name);

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * {@inheritDoc}
     *
     * @return the value returned by {@link #getName()}
     */
    @Override
    public String toString() {
        return name;
    }
}
