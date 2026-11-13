/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.SubsystemModelTransformerRegistration;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;

public class RemotingTransformerRegistration extends SubsystemModelTransformerRegistration<RemotingSubsystemModel> {

    public RemotingTransformerRegistration() {
        super(RemotingExtension.SUBSYSTEM_NAME, RemotingSubsystemModel.CURRENT, RemotingTransformerRegistration::register);
    }

    private static void register(ResourceTransformationDescriptionBuilder builder, ModelVersion version) {
        if (RemotingSubsystemModel.VERSION_8_0_0.requiresTransformation(version)) {
            builder.addChildResource(ConnectorResource.PATH)
                    .getAttributeBuilder()
                    .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(new ModelNode("remote")), ConnectorResource.PROTOCOL)
                    .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorResource.PROTOCOL)
                    .end();
        }
    }
}
