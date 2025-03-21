/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;

import java.util.function.Function;

public enum RemotingSubsystemTransformationDescriptionFactory implements Function<ModelVersion, TransformationDescription> {
    INSTANCE;

    @Override
    public TransformationDescription apply(ModelVersion version) {
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createSubsystemInstance();
        if (RemotingSubsystemModel.VERSION_8_0_0.requiresTransformation(version)) {
            builder.addChildResource(ConnectorResource.PATH)
                    .getAttributeBuilder()
                    .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(new ModelNode("remote")),ConnectorResource.PROTOCOL)
                    .addRejectCheck(RejectAttributeChecker.DEFINED,ConnectorResource.PROTOCOL)
                    .end();
        }
        return builder.build();
    }
}