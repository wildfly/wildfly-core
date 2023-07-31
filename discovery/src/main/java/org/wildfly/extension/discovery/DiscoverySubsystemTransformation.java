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
 * Creates the transformation descriptions for the discovery subsystem resource.
 * @author Paul Ferraro
 */
public class DiscoverySubsystemTransformation implements Function<ModelVersion, TransformationDescription> {

    private final ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createSubsystemInstance();

    @Override
    public TransformationDescription apply(ModelVersion version) {
        return this.builder.build();
    }
}
