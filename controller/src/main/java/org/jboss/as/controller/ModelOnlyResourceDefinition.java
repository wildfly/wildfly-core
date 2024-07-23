/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * {@link ResourceDefinition} implementation designed for use in extensions
 * based on {@link org.jboss.as.controller.extension.AbstractLegacyExtension}. Takes the {@link AttributeDefinition}s provided to the constructor
 * and uses them to create a {@link ModelOnlyAddStepHandler} for handling {@code add} operations, and to
 * create a {@link ModelOnlyWriteAttributeHandler} for handling {@code write-attribute} operations. The
 * {@link ModelOnlyRemoveStepHandler} is used for {@code remove} operations.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class ModelOnlyResourceDefinition extends SimpleResourceDefinition {

    private final AttributeDefinition[] attributes;

    public ModelOnlyResourceDefinition(Parameters parameters, AttributeDefinition... attributes) {
        super(parameters);
        this.attributes = attributes;
    }

    public ModelOnlyResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, AttributeDefinition... attributes) {
        super(pathElement, descriptionResolver, ModelOnlyAddStepHandler.INSTANCE, ModelOnlyRemoveStepHandler.INSTANCE);
        this.attributes = attributes;
    }

    public ModelOnlyResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, ModelOnlyAddStepHandler addStepHandler, AttributeDefinition... attributes) {
        super(pathElement, descriptionResolver, addStepHandler, ModelOnlyRemoveStepHandler.INSTANCE);
        this.attributes = attributes;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition ad : attributes) {
            resourceRegistration.registerReadWriteAttribute(ad, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }
    }
}
