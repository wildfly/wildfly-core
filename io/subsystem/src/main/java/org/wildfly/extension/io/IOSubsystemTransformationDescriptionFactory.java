/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.function.Function;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;

/**
 * Generates a transformation description for the IO subsystem.
 */
public enum IOSubsystemTransformationDescriptionFactory implements Function<ModelVersion, TransformationDescription> {
    INSTANCE;

    @Override
    public TransformationDescription apply(ModelVersion version) {
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createSubsystemInstance();
        if (IOSubsystemModel.VERSION_6_0_0.requiresTransformation(version)) {
            builder.getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(IOSubsystemResourceDefinitionRegistrar.LEGACY_DEFAULT_WORKER), IOSubsystemResourceDefinitionRegistrar.DEFAULT_WORKER)
                .addRejectCheck(RejectAttributeChecker.DEFINED, IOSubsystemResourceDefinitionRegistrar.DEFAULT_WORKER)
                .end();
        }
        return builder.build();
    }
}
