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
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public class RemotingTransformers implements ExtensionTransformerRegistration {
    private static final ModelVersion VERSION_1_3 = ModelVersion.create(1, 3); //eap 6.3 & eap 6.2
    private static final ModelVersion VERSION_1_4 = ModelVersion.create(1, 4); //eap 6.4
    private static final ModelVersion VERSION_3_0 = ModelVersion.create(3, 0);

    @Override
    public String getSubsystemName() {
        return RemotingExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {

        // We need separate chains for 1.x and >= 3.0 as the handling of RemotingEndpointResource differs
        // CRITICAL! Any transformer to 4.0.0 or later MUST be added to both chains

        // First >- 3.0
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());

        // 4.0.0 to 3.0.0
        buildTransformers_3_0(chainedBuilder.createBuilder(registration.getCurrentSubsystemVersion(), VERSION_3_0));

        chainedBuilder.buildAndRegister(registration, new ModelVersion[]{VERSION_3_0});

        // Next, the 1.x chain
        ChainedTransformationDescriptionBuilder chained1xBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());
        // 4.0.0 to 1.4.0
        buildTransformers_1_4(chained1xBuilder.createBuilder(registration.getCurrentSubsystemVersion(), VERSION_1_4));
        // 1.4.0 to 1.3.0
        buildTransformers_1_3(chained1xBuilder.createBuilder(VERSION_1_4, VERSION_1_3));

        chained1xBuilder.buildAndRegister(registration, new ModelVersion[]{VERSION_1_3, VERSION_1_4});
    }

    private void buildTransformers_1_4(ResourceTransformationDescriptionBuilder builder) {
        builder.addChildResource(ConnectorResource.PATH).getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY, ConnectorResource.SSL_CONTEXT)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY, ConnectorResource.SSL_CONTEXT);

        builder.rejectChildResource(HttpConnectorResource.PATH);
        builder.addChildResource(RemotingEndpointResource.ENDPOINT_PATH)
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, RemotingEndpointResource.ATTRIBUTES)
                .addRejectCheck(RejectAttributeChecker.DEFINED, RemotingEndpointResource.ATTRIBUTES.toArray(new AttributeDefinition[RemotingEndpointResource.ATTRIBUTES.size()]))
                .end()
                .addOperationTransformationOverride(ModelDescriptionConstants.ADD)
                .inheritResourceAttributeDefinitions()
                .setCustomOperationTransformer(OperationTransformer.DISCARD)
                .end()
                .setCustomResourceTransformer(ResourceTransformer.DISCARD);
        builder.addChildResource(RemoteOutboundConnectionResourceDefinition.ADDRESS).getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(new ModelNode(Protocol.REMOTE.toString())), RemoteOutboundConnectionResourceDefinition.PROTOCOL)
                .addRejectCheck(RejectAttributeChecker.DEFINED, RemoteOutboundConnectionResourceDefinition.PROTOCOL)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY)
                .addRejectCheck(RejectAttributeChecker.DEFINED, RemoteOutboundConnectionResourceDefinition.AUTHENTICATION_CONTEXT);
    }

    private void buildTransformers_1_3(ResourceTransformationDescriptionBuilder builder) {
        builder.addChildResource(ConnectorResource.PATH).getAttributeBuilder()
                .setValueConverter(new AttributeConverter.DefaultValueAttributeConverter(ConnectorCommon.SASL_PROTOCOL), ConnectorCommon.SASL_PROTOCOL)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ConnectorCommon.SASL_PROTOCOL, ConnectorCommon.SERVER_NAME)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SASL_PROTOCOL, ConnectorCommon.SERVER_NAME);
    }

    //EAP 7.0
    private void buildTransformers_3_0(ResourceTransformationDescriptionBuilder builder) {
        /*
        ====== Resource root address: ["subsystem" => "remoting"] - Current version: 4.0.0; legacy version: 3.0.0 =======
        --- Problems for relative address to root ["configuration" => "endpoint"]:
        Different 'default' for attribute 'sasl-protocol'. Current: "remote"; legacy: "remoting" ## both are valid also for legacy servers
        --- Problems for relative address to root ["connector" => "*"]:
        Missing attributes in current: []; missing in legacy [sasl-authentication-factory, ssl-context]
        Missing parameters for operation 'add' in current: []; missing in legacy [sasl-authentication-factory, ssl-context]
        --- Problems for relative address to root ["http-connector" => "*"]:
        Missing attributes in current: []; missing in legacy [sasl-authentication-factory]
        Missing parameters for operation 'add' in current: []; missing in legacy [sasl-authentication-factory]
        --- Problems for relative address to root ["remote-outbound-connection" => "*"]:
        Missing attributes in current: []; missing in legacy [authentication-context]
        Different 'alternatives' for attribute 'protocol'. Current: ["authentication-context"]; legacy: undefined
        Different 'alternatives' for attribute 'security-realm'. Current: ["authentication-context"]; legacy: undefined
        Different 'alternatives' for attribute 'username'. Current: ["authentication-context"]; legacy: undefined
        Missing parameters for operation 'add' in current: []; missing in legacy [authentication-context]
        Different 'alternatives' for parameter 'protocol' of operation 'add'. Current: ["authentication-context"]; legacy: undefined
        Different 'alternatives' for parameter 'security-realm' of operation 'add'. Current: ["authentication-context"]; legacy: undefined
        Different 'alternatives' for parameter 'username' of operation 'add'. Current: ["authentication-context"]; legacy: undefined
         */

        builder.addChildResource(ConnectorResource.PATH).getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY, ConnectorResource.SSL_CONTEXT)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY, ConnectorResource.SSL_CONTEXT);

        builder.addChildResource(RemotingEndpointResource.ENDPOINT_PATH).getAttributeBuilder()
                .setValueConverter(new AttributeConverter.DefaultAttributeConverter() {
                    @Override
                    protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
                        if (!attributeValue.isDefined()) {
                            attributeValue.set("remoting"); //if value is not defined, set it to EAP 7.0 default valueRemotingSubsystemTransformersTestCase
                        }
                    }
                }, RemotingEndpointResource.SASL_PROTOCOL);

        builder.addChildResource(HttpConnectorResource.PATH).getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY);

        builder.addChildResource(RemoteOutboundConnectionResourceDefinition.ADDRESS).getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ConnectorCommon.SASL_AUTHENTICATION_FACTORY)
                .addRejectCheck(RejectAttributeChecker.DEFINED, RemoteOutboundConnectionResourceDefinition.AUTHENTICATION_CONTEXT);
    }
}
