/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public class RemotingSubsystemExtensionTransformerRegistration implements ExtensionTransformerRegistration {
    private static final ModelVersion VERSION_1_3 = ModelVersion.create(1, 3);
    private static final ModelVersion VERSION_1_4 = ModelVersion.create(1, 4);
    private static final ModelVersion VERSION_2_1 = ModelVersion.create(2, 1);
    private static final ModelVersion VERSION_3_0 = ModelVersion.create(3, 0);

    @Override
    public String getSubsystemName() {
        return RemotingExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());

        // Current 4.0.0 to 3.0.0
        buildTransformers_3_0(chainedBuilder.createBuilder(registration.getCurrentSubsystemVersion(), VERSION_3_0));

        // Current 3.0.0 to 2.1.0
        buildTransformers_2_1(chainedBuilder.createBuilder(VERSION_3_0, VERSION_2_1));

        // Current 3.0.0 to 2.1.0
        buildTransformers_1_4(chainedBuilder.createBuilder(VERSION_2_1, VERSION_1_4));

        //2.1.0 to 1.3.0
        buildTransformers_1_3(chainedBuilder.createBuilder(VERSION_1_4, VERSION_1_3));


        chainedBuilder.buildAndRegister(registration, new ModelVersion[]{VERSION_1_3, VERSION_1_4, VERSION_2_1, VERSION_3_0});
    }

    private void buildTransformers_1_4(ResourceTransformationDescriptionBuilder builder) {
        builder.rejectChildResource(HttpConnectorResource.PATH);
        endpointTransform(builder);
        builder.addChildResource(RemoteOutboundConnectionResourceDefinition.ADDRESS).getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(new ModelNode(Protocol.REMOTE.toString())), RemoteOutboundConnectionResourceDefinition.PROTOCOL)
                .addRejectCheck(RejectAttributeChecker.DEFINED, RemoteOutboundConnectionResourceDefinition.PROTOCOL);
    }

    private void buildTransformers_1_3(ResourceTransformationDescriptionBuilder builder) {
        //Nothing (the 1.3 changes are handled by the 2.1 transformer)
    }

    private void buildTransformers_2_1(ResourceTransformationDescriptionBuilder builder) {
        builder.addChildResource(ConnectorResource.PATH).getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(ConnectorCommon.SASL_PROTOCOL.getDefaultValue()), ConnectorCommon.SASL_PROTOCOL)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SASL_PROTOCOL)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SERVER_NAME);

        builder.addChildResource(HttpConnectorResource.PATH).getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(ConnectorCommon.SASL_PROTOCOL.getDefaultValue()), ConnectorCommon.SASL_PROTOCOL)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SASL_PROTOCOL)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SERVER_NAME);
    }

    private void buildTransformers_3_0(ResourceTransformationDescriptionBuilder builder) {
        builder.addChildResource(ConnectorResource.PATH).getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorResource.SSL_CONTEXT);

        builder.addChildResource(HttpConnectorResource.PATH).getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY);
    }

    private static ResourceTransformationDescriptionBuilder endpointTransform(ResourceTransformationDescriptionBuilder parent) {
        // For configuration=endpoint, reject if any attributes are defined, otherwise discard the add op and the resource
        parent.addChildResource(RemotingEndpointResource.ENDPOINT_PATH)
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, RemotingEndpointResource.ATTRIBUTES)
                .addRejectCheck(RejectAttributeChecker.DEFINED, RemotingEndpointResource.ATTRIBUTES.toArray(new AttributeDefinition[RemotingEndpointResource.ATTRIBUTES.size()]))
                .end()
                .addOperationTransformationOverride(ModelDescriptionConstants.ADD)
                .inheritResourceAttributeDefinitions()
                .setCustomOperationTransformer(OperationTransformer.DISCARD)
                .end()
                .setCustomResourceTransformer(ResourceTransformer.DISCARD);
        return parent;
    }
}
