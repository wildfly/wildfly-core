/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.discovery;

import java.util.function.Function;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;

/**
 * Creates transformation descriptions for the discovery subsystem.
 * @author Paul Ferraro
 */
public enum DiscoverySubsystemTransformationDescriptionFactory implements Function<ModelVersion, TransformationDescription> {
    INSTANCE;

    @Override
    public TransformationDescription apply(ModelVersion version) {
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createSubsystemInstance();
        return builder.build();
    }
}
