/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import java.util.EnumSet;
import java.util.function.BiConsumer;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;

/**
 * A transformer registration of a single subsystem.
 */
public class SubsystemModelTransformerRegistration<E extends Enum<E> & SubsystemModel> implements ExtensionTransformerRegistration {

    private final String name;
    private final E currentModel;
    private final BiConsumer<ResourceTransformationDescriptionBuilder, ModelVersion> transformation;

    /**
     * Creates a transformer registration for a subsystem.
     * @param registration the subsystem registration
     * @param currentModel the current subsystem model
     * @param transformation a consumer for accumulating model transformers for a given target model version
     */
    protected SubsystemModelTransformerRegistration(SubsystemResourceRegistration registration, E currentModel, BiConsumer<ResourceTransformationDescriptionBuilder, ModelVersion> transformation) {
        this(registration.getName(), currentModel, transformation);
    }

    /**
     * Creates a transformer registration for a subsystem.
     * @param name the subsystem name
     * @param currentModel the current subsystem model
     * @param transformation a consumer for accumulating model transformers for a given target model version
     */
    protected SubsystemModelTransformerRegistration(String name, E currentModel, BiConsumer<ResourceTransformationDescriptionBuilder, ModelVersion> transformation) {
        this.name = name;
        this.currentModel = currentModel;
        this.transformation = transformation;
    }

    @Override
    public String getSubsystemName() {
        return this.name;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        // Build and register transformation descriptions for all but the current subsystem model version
        for (E model : EnumSet.complementOf(EnumSet.of(this.currentModel))) {
            ModelVersion version = model.getVersion();
            ResourceTransformationDescriptionBuilder builder = registration.createResourceTransformationDescriptionBuilder();
            this.transformation.accept(builder, version);
            TransformationDescription.Tools.register(builder.build(), registration, version);
        }
    }
}
