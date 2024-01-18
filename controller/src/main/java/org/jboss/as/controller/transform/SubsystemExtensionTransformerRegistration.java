/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import java.util.EnumSet;
import java.util.function.Function;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;
import org.jboss.as.controller.transform.description.TransformationDescription;

/**
 * Registers transformers for a single subsystem.
 */
public class SubsystemExtensionTransformerRegistration implements ExtensionTransformerRegistration {

    private final String name;
    private final Iterable<? extends SubsystemModel> legacyModels;
    private final Function<ModelVersion, TransformationDescription> factory;

    protected <E extends Enum<E> & SubsystemModel> SubsystemExtensionTransformerRegistration(String name, E currentModel, Function<ModelVersion, TransformationDescription> factory) {
        this.name = name;
        this.legacyModels = EnumSet.complementOf(EnumSet.of(currentModel));
        this.factory = factory;
    }

    @Override
    public String getSubsystemName() {
        return this.name;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration subsystemRegistration) {
        for (SubsystemModel legacyModel : this.legacyModels) {
            ModelVersion legacyVersion = legacyModel.getVersion();
            TransformationDescription.Tools.register(this.factory.apply(legacyVersion), subsystemRegistration, legacyVersion);
        }
    }
}
