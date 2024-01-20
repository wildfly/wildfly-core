/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.List;
import java.util.function.Supplier;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.wildfly.common.function.Functions;

/**
 * Generalized {@link ResourceDefinition} decorator.
 */
public class ProvidedResourceDefinition implements ResourceDefinition {

    private final Supplier<ResourceDefinition> provider;

    public ProvidedResourceDefinition(ResourceDefinition definition) {
        this(Functions.constantSupplier(definition));
    }

    public ProvidedResourceDefinition(Supplier<ResourceDefinition> provider) {
        this.provider = provider;
    }

    @Override
    public PathElement getPathElement() {
        return this.provider.get().getPathElement();
    }

    @Override
    public DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration registration) {
        return this.provider.get().getDescriptionProvider(registration);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registration) {
        this.provider.get().registerOperations(registration);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        this.provider.get().registerAttributes(registration);
    }

    @Override
    public void registerNotifications(ManagementResourceRegistration registration) {
        this.provider.get().registerNotifications(registration);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration registration) {
        this.provider.get().registerChildren(registration);
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration registration) {
        this.provider.get().registerCapabilities(registration);
    }

    @Override
    public void registerAdditionalRuntimePackages(ManagementResourceRegistration registration) {
        this.provider.get().registerAdditionalRuntimePackages(registration);
    }

    @Override
    public boolean isFeature() {
        return this.provider.get().isFeature();
    }

    @Override
    public boolean isOrderedChild() {
        return this.provider.get().isOrderedChild();
    }

    @Override
    public boolean isRuntime() {
        return this.provider.get().isRuntime();
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return this.provider.get().getAccessConstraints();
    }

    @Override
    public int getMaxOccurs() {
        return this.provider.get().getMaxOccurs();
    }

    @Override
    public int getMinOccurs() {
        return this.provider.get().getMinOccurs();
    }

    @Override
    public Stability getStability() {
        return this.provider.get().getStability();
    }
}
