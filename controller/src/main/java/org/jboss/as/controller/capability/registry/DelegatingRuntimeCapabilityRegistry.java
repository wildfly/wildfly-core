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
 * {@link RuntimeCapabilityRegistry} implementation that simply delegates to another
 * {@link RuntimeCapabilityRegistry}. Intended as a convenience class to allow overriding
 * of standard behaviors and also as a means to support a copy-on-write/publish-on-commit
 * semantic for the management resource tree.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class DelegatingRuntimeCapabilityRegistry implements RuntimeCapabilityRegistry {

    /**
     * Provides a delegate for use by a {@code DelegatingCapabilityRegistry}.
     * Does not need to provide the same delegate for every call, allowing a copy-on-write
     * semantic for the underlying {@code CapabilityRegistry}.
     */
    public interface CapabilityRegistryDelegateProvider {
        /**
         * Gets the delegate.
         * @return the delegate. Cannot return {@code null}
         */
        RuntimeCapabilityRegistry getDelegateCapabilityRegistry();
    }

    private final CapabilityRegistryDelegateProvider delegateProvider;

    /**
     * Creates a new DelegatingCapabilityRegistry with a fixed delegate.
     *
     * @param delegate the delegate. Cannot be {@code null}
     */
    public DelegatingRuntimeCapabilityRegistry(final RuntimeCapabilityRegistry delegate) {
        this(new CapabilityRegistryDelegateProvider() {
            @Override
            public RuntimeCapabilityRegistry getDelegateCapabilityRegistry() {
                return delegate;
            }
        });
    }

    /**
     * Creates a new DelegatingResource with a possibly changing delegate.
     *
     * @param delegateProvider provider of the delegate. Cannot be {@code null}
     */
    public DelegatingRuntimeCapabilityRegistry(CapabilityRegistryDelegateProvider delegateProvider) {
        this.delegateProvider = delegateProvider;
    }

    @Override
    public void registerCapability(RuntimeCapabilityRegistration capability) {
        getDelegate().registerCapability(capability);
    }

    @Override
    public void registerAdditionalCapabilityRequirement(RuntimeRequirementRegistration requirementRegistration) {
        getDelegate().registerAdditionalCapabilityRequirement(requirementRegistration);
    }

    @Override
    public void removeCapabilityRequirement(RequirementRegistration requirementRegistration) {
        getDelegate().removeCapabilityRequirement(requirementRegistration);
    }

    @Override
    public RuntimeCapabilityRegistration removeCapability(String capability, CapabilityContext context) {
        return getDelegate().removeCapability(capability, context);
    }

    @Override
    public boolean hasCapability(String capabilityName, CapabilityContext context) {
        return getDelegate().hasCapability(capabilityName, context);
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityName, CapabilityContext context, Class<T> apiType) {
        return getDelegate().getCapabilityRuntimeAPI(capabilityName, context, apiType);
    }

    private RuntimeCapabilityRegistry getDelegate() {
        return delegateProvider.getDelegateCapabilityRegistry();
    }
}
