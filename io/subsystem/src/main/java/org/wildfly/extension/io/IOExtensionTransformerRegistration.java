/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.SubsystemModelTransformerRegistration;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;

/**
 * Registers model transformations for the IO subsystem.
 */
public class IOExtensionTransformerRegistration extends SubsystemModelTransformerRegistration<IOSubsystemModel> {

    public IOExtensionTransformerRegistration() {
        super(IOSubsystemResourceDefinitionRegistrar.REGISTRATION, IOSubsystemModel.CURRENT, IOExtensionTransformerRegistration::register);
    }

    private static void register(ResourceTransformationDescriptionBuilder builder, ModelVersion version) {
        if (IOSubsystemModel.VERSION_6_0_0.requiresTransformation(version)) {
            builder.getAttributeBuilder()
                    .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(IOSubsystemResourceDefinitionRegistrar.LEGACY_DEFAULT_WORKER), IOSubsystemResourceDefinitionRegistrar.DEFAULT_WORKER)
                    .addRejectCheck(RejectAttributeChecker.DEFINED, IOSubsystemResourceDefinitionRegistrar.DEFAULT_WORKER)
                    .end();
        }
    }
}
