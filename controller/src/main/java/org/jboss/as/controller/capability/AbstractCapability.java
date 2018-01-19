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
 *
 */
abstract class AbstractCapability implements Capability {

    private final String name;
    private final boolean dynamic;
    private final Set<String> requirements;
    final Function<PathAddress,String[]> dynamicNameMapper;

    AbstractCapability(final String name,
                       final boolean dynamic,
                       final Set<String> requirements,
                       Function<PathAddress, String[]> dynamicNameMapper) {
        assert name != null;
        this.name = name;
        this.dynamic = dynamic;
        this.requirements = establishRequirements(requirements);
        if (dynamicNameMapper != null) {
            this.dynamicNameMapper = dynamicNameMapper;
        } else {
            this.dynamicNameMapper = AbstractCapability::addressValueToDynamicName;
        }
    }

    private static Set<String> establishRequirements(Set<String> input) {
        if (input != null && !input.isEmpty()) {
            return Collections.unmodifiableSet(new HashSet<>(input));
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Resolves the dynamic elements of a capability name from a path address by returning last element value.
     *
     * @param pathAddress the address. Cannot be {@code null}
     * @return dynamic part of the capability name
     *
     */
    static String[] addressValueToDynamicName(PathAddress pathAddress){
        return new String[]{pathAddress.getLastElement().getValue()};
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getRequirements() {
        return requirements;
    }

    @Override
    public boolean isDynamicallyNamed() {
        return dynamic;
    }

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
