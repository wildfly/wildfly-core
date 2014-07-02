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
import java.util.Locale;
import java.util.Set;

/**
 * Base class for a core or subsystem capability.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public abstract class AbstractCapability {

    private final String name;
    private final Set<String> requirements;
    private final Set<String> optionalRequirements;

    protected AbstractCapability(final String name, final Set<String> requirements, final Set<String> optionalRequirements) {
        assert name != null;
        this.name = name;
        if (requirements != null && !requirements.isEmpty()) {
            this.requirements = Collections.unmodifiableSet(new HashSet<String>(requirements));
        } else {
            this.requirements = Collections.emptySet();
        }
        if (optionalRequirements != null && !optionalRequirements.isEmpty()) {
            this.optionalRequirements = Collections.unmodifiableSet(new HashSet<String>(optionalRequirements));
        } else {
            this.optionalRequirements = Collections.emptySet();
        }
    }

    protected AbstractCapability(final String name, final String... requirements) {
        assert name != null;
        this.name = name;
        if (requirements != null && requirements.length > 0) {
            Set<String> set = new HashSet<>(requirements.length);
            Collections.addAll(set, requirements);
            this.requirements = Collections.unmodifiableSet(set);
        } else {
            this.requirements = Collections.emptySet();
        }
        this.optionalRequirements = Collections.emptySet();
    }

    /**
     * Gets the name of the capability.
     *
     * @return the name. Will not be {@code null}
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the names of other capabilities required by this capability.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     */
    public Set<String> getRequirements() {
        return requirements;
    }

    /**
     * Gets the names of other capabilities optionally required by this capability.
     *
     * @return the capability names. Will not be {@code null} but may be empty.
     */
    public Set<String> getOptionalRequirements() {
        return optionalRequirements;
    }

    /**
     * Gets an internationalized text description of the capability.
     *
     * @param locale the locale to use. Cannot be {@code null}
     * @return the text description, or {@code null} if there is no text description
     *
     * @throws java.lang.IllegalArgumentException if {@code locale} is {@code null}
     */
    public abstract String getDescription(Locale locale);

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
