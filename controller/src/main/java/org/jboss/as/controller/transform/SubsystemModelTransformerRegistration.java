/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import java.util.EnumSet;
import java.util.function.BiConsumer;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;

/**
 * A transformer registration of a single subsystem.
 */
public class SubsystemModelTransformerRegistration<E extends Enum<E> & SubsystemModel> implements ExtensionTransformerRegistration {

    private final String subsystemName;
    private final E currentSubsystemModel;
    private final BiConsumer<ResourceTransformationDescriptionBuilder, ModelVersion> transformation;

    /**
     * Creates a transformer registration for a subsystem.
     * @param subsystemName the subsystem name
     * @param currentSubsystemModel the current subsystem model
     * @param transformation a consumer that builds a transformer description for a given target model version
     */
    public SubsystemModelTransformerRegistration(String subsystemName, E currentSubsystemModel, BiConsumer<ResourceTransformationDescriptionBuilder, ModelVersion> transformation) {
        this.subsystemName = subsystemName;
        this.currentSubsystemModel = currentSubsystemModel;
        this.transformation = transformation;
    }

    @Override
    public String getSubsystemName() {
        return this.subsystemName;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        // Build and register transformation descriptions for all but the current subsystem model version
        for (E model : EnumSet.complementOf(EnumSet.of(this.currentSubsystemModel))) {
            ModelVersion version = model.getVersion();
            ResourceTransformationDescriptionBuilder builder = registration.createResourceTransformationDescriptionBuilder();
            this.transformation.accept(builder, version);
            TransformationDescription.Tools.register(builder.build(), registration, version);
        }
    }
}
