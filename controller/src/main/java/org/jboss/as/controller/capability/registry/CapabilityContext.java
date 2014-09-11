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

/**
 * Context in which a {@link org.jboss.as.controller.capability.AbstractCapability capability} is available.
 * <p>
 * The {@link #GLOBAL} context can be used for most cases. A Host Controller will use a different implementation
 * of this interface for capabilities that are limited to some subset of the domain-wide model, e.g. a single
 * profile.
 * </p>
 * <p>
 * Implementations of this interface should override {@link #equals(Object)} and {@link #hashCode()} such that
 * logically equivalent but non-identical instances can function as keys in a hash map.
 * </p>
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public interface CapabilityContext {

    /**
     * Gets whether this context can satisfy a requirement associated with another context.
     * @param dependentContext a context associated with a capability that may require the capabilities associated
     *                         with this context
     * @return {@code true} if this context can satisfy requirements from capabilities in {@code dependentContext}
     */
    boolean canSatisfyRequirements(CapabilityContext dependentContext);

    /**
     * Gets a descriptive name of the context
     * @return the name. Will not return {@code null}
     */
    String getName();

    /**
     * A {@code CapabilityContext} that can satisfy any dependent context. Meant for capabilities that are present
     * regardless of any context, or for convenience use in cases where there is only one context.
     */
    CapabilityContext GLOBAL = new CapabilityContext() {
        /**
         * Always returns {@code true}
         * @return {@code true}, always
         */
        @Override
        public boolean canSatisfyRequirements(CapabilityContext dependentContext) {
            return true;
        }

        @Override
        public String getName() {
            return "global";
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{global}";
        }
    };
}
