/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.remoting;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_6_4_0;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_0_0;
import static org.jboss.as.remoting.RemotingSubsystemTestUtil.DEFAULT_ADDITIONAL_INITIALIZATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012 Red Hat, inc
 */
public class RemotingSubsystemTransformersTestCase extends AbstractSubsystemTest {

    private static final AttributeDefinition[] ENDPOINT_ATTRS = RemotingEndpointResource.ATTRIBUTES.values().toArray(new AttributeDefinition[RemotingEndpointResource.ATTRIBUTES.size()]);

    public RemotingSubsystemTransformersTestCase() {
        super(RemotingExtension.SUBSYSTEM_NAME, new RemotingExtension());
    }


    @Test
    public void testTransformersEAP640() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION)
                .setSubsystemXmlResource("remoting-transformers.xml");

        ModelTestControllerVersion controllerVersion = ModelTestControllerVersion.EAP_6_4_0;
        ModelVersion oldVersion = controllerVersion.getSubsystemModelVersion(getMainSubsystemName());

        // Add legacy subsystems
        builder.createLegacyKernelServicesBuilder(null, controllerVersion)
                .addMavenResourceURL("org.jboss.as:jboss-as-remoting:" + controllerVersion.getMavenGavVersion())
                .skipReverseControllerCheck();
        //.configureReverseControllerCheck(createAdditionalInitialization(), null);
        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        KernelServices legacyServices = mainServices.getLegacyServices(oldVersion);
        assertNotNull(legacyServices);
        assertTrue(legacyServices.isSuccessfulBoot());

        checkSubsystemModelTransformation(mainServices, oldVersion, null, false);
        checkRejectOutboundConnectionProtocolNotRemote(mainServices, oldVersion, CommonAttributes.REMOTE_OUTBOUND_CONNECTION, "remote-conn1");
        checkRejectHttpConnector(mainServices, oldVersion);
        checkRejectEndpointConfiguration(mainServices, oldVersion);
    }


    @Test
    public void testTransformersEAP700() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION)
                .setSubsystemXmlResource("remoting-with-expressions-and-good-legacy-protocol.xml");
        ModelTestControllerVersion controllerVersion = ModelTestControllerVersion.EAP_7_0_0;

        ModelVersion oldVersion = controllerVersion.getSubsystemModelVersion(getMainSubsystemName());
        // Add legacy subsystems
        builder.createLegacyKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION, controllerVersion)
                .addMavenResourceURL("org.wildfly.core:wildfly-remoting:" + controllerVersion.getCoreVersion())
                .configureReverseControllerCheck(DEFAULT_ADDITIONAL_INITIALIZATION, null);
        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        KernelServices legacyServices = mainServices.getLegacyServices(oldVersion);
        assertNotNull(legacyServices);
        assertTrue(legacyServices.isSuccessfulBoot());

        checkSubsystemModelTransformation(mainServices, oldVersion, null, false);
    }

    @Test
    public void testRejectingTransformersEAP_7_0_0() throws Exception {
        testRejectingTransformers(EAP_7_0_0, EAP_7_0_0.getSubsystemModelVersion(getMainSubsystemName()));
    }

    @Test
    public void testRejectingTransformersEAP_6_4_0() throws Exception {
        testRejectingTransformers64(EAP_6_4_0);
    }

    private void testRejectingTransformers64(ModelTestControllerVersion controllerVersion) throws Exception {
        ModelVersion targetModelVersion = controllerVersion.getSubsystemModelVersion(getMainSubsystemName());
        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION);
        builder.createLegacyKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION, controllerVersion, targetModelVersion)
                .addMavenResourceURL(controllerVersion.getMavenGav("remoting", true))
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(targetModelVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource("remoting-4.0.xml");
        PathAddress subsystemAddress = PathAddress.pathAddress("subsystem", RemotingExtension.SUBSYSTEM_NAME);
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, targetModelVersion, ops, new FailedOperationTransformationConfig()
                .addFailedAttribute(subsystemAddress,
                        new FailedOperationTransformationConfig.NewAttributesConfig(ENDPOINT_ATTRS))
                .addFailedAttribute(subsystemAddress.append(RemotingEndpointResource.ENDPOINT_PATH),
                        new FailedOperationTransformationConfig.NewAttributesConfig(ENDPOINT_ATTRS))
                .addFailedAttribute(subsystemAddress.append(ConnectorResource.PATH),
                        new FailedOperationTransformationConfig.NewAttributesConfig(
                                ConnectorCommon.SASL_AUTHENTICATION_FACTORY,
                                ConnectorResource.SSL_CONTEXT
                        )
                )
                .addFailedAttribute(subsystemAddress.append(HttpConnectorResource.PATH), FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(subsystemAddress.append(HttpConnectorResource.PATH).append(PropertyResource.PATH), FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(subsystemAddress.append(HttpConnectorResource.PATH).append(SaslResource.SASL_CONFIG_PATH), FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(subsystemAddress.append(HttpConnectorResource.PATH).append(SaslResource.SASL_CONFIG_PATH).append(PropertyResource.PATH), FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(subsystemAddress.append(HttpConnectorResource.PATH).append(SaslResource.SASL_CONFIG_PATH).append(SaslPolicyResource.SASL_POLICY_CONFIG_PATH), FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(subsystemAddress.append(RemoteOutboundConnectionResourceDefinition.ADDRESS),
                        new FailedOperationTransformationConfig.NewAttributesConfig(ConnectorCommon.SASL_AUTHENTICATION_FACTORY))
        );
    }

    private void testRejectingTransformers(ModelTestControllerVersion controllerVersion, ModelVersion messagingVersion) throws Exception {
        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION);
        builder.createLegacyKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION, controllerVersion, messagingVersion)
                .addMavenResourceURL(controllerVersion.getCoreMavenGroupId() + ":wildfly-remoting:" + controllerVersion.getCoreVersion())
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(messagingVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource("remoting-4.0.xml");
        PathAddress subsystemAddress = PathAddress.pathAddress("subsystem", RemotingExtension.SUBSYSTEM_NAME);
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, messagingVersion, ops, new FailedOperationTransformationConfig()
                .addFailedAttribute(subsystemAddress.append(ConnectorResource.PATH),
                        new FailedOperationTransformationConfig.NewAttributesConfig(
                                ConnectorCommon.SASL_AUTHENTICATION_FACTORY,
                                ConnectorResource.SSL_CONTEXT
                        )
                )
                .addFailedAttribute(subsystemAddress.append(HttpConnectorResource.PATH),
                        new FailedOperationTransformationConfig.NewAttributesConfig(ConnectorCommon.SASL_AUTHENTICATION_FACTORY))
                .addFailedAttribute(subsystemAddress.append(RemoteOutboundConnectionResourceDefinition.ADDRESS),
                        new FailedOperationTransformationConfig.NewAttributesConfig(ConnectorCommon.SASL_AUTHENTICATION_FACTORY))
        );
    }


    private void checkRejectOutboundConnectionProtocolNotRemote(KernelServices mainServices, ModelVersion version, String type, String name) throws OperationFailedException {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
        address.add(type, name);
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set(CommonAttributes.PROTOCOL);
        operation.get(VALUE).set(Protocol.HTTP_REMOTING.toString());

        checkReject(operation, mainServices, version);

        PathAddress addr = PathAddress.pathAddress(operation.get(OP_ADDR));
        PathElement element = addr.getLastElement();
        addr = addr.subAddress(0, addr.size() - 1);
        addr = addr.append(PathElement.pathElement(element.getKey(), "remoting-outbound2"));

        operation = Util.createAddOperation(addr);
        operation.get(CommonAttributes.OUTBOUND_SOCKET_BINDING_REF).set("dummy-outbound-socket");
        operation.get(CommonAttributes.USERNAME).set("myuser");
        operation.get(CommonAttributes.PROTOCOL).set(Protocol.HTTP_REMOTING.toString());
        checkReject(operation, mainServices, version);

    }

    private void checkRejectHttpConnector(KernelServices mainServices, ModelVersion version) throws OperationFailedException {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(ADD);
        operation.get(CommonAttributes.CONNECTOR_REF).set("test");
        ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
        address.add(HttpConnectorResource.PATH.getKey(), "test");
        operation.get(OP_ADDR).set(address);

        checkReject(operation, mainServices, version);
    }

    private void checkRejectEndpointConfiguration(KernelServices mainServices, ModelVersion version) throws OperationFailedException {

        // First clean out any worker-thread-pool stuff from earlier testing
        ModelNode operation = new ModelNode();
        operation.get(OP).set(UNDEFINE_ATTRIBUTE_OPERATION);
        ModelNode address = operation.get(OP_ADDR);
        address.add(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
        for (AttributeDefinition ad : RemotingSubsystemRootResource.LEGACY_ATTRIBUTES) {
            operation.get(NAME).set(ad.getName());
            ModelNode mainResult = mainServices.executeOperation(operation);
            assertEquals(mainResult.toJSONString(true), SUCCESS, mainResult.get(OUTCOME).asString());
        }

        operation = new ModelNode();
        operation.get(OP).set(ADD);
        address = new ModelNode();
        address.add(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
        address.add(RemotingEndpointResource.ENDPOINT_PATH.getKey(), RemotingEndpointResource.ENDPOINT_PATH.getValue());
        operation.get(OP_ADDR).set(address);
        for (AttributeDefinition ad : RemotingEndpointResource.ATTRIBUTES.values()) {
            ModelNode dflt = ad.getDefaultValue();
            if (dflt != null) {
                operation.get(ad.getName()).set(dflt);
            }
        }

        checkReject(operation, mainServices, version);
    }

    private void checkReject(ModelNode operation, KernelServices mainServices, ModelVersion version) throws OperationFailedException {

        ModelNode mainResult = mainServices.executeOperation(operation);
        assertEquals(mainResult.toJSONString(true), SUCCESS, mainResult.get(OUTCOME).asString());

        final OperationTransformer.TransformedOperation op = mainServices.transformOperation(version, operation);
        final ModelNode result = mainServices.executeOperation(version, op);
        assertEquals("should reject the operation", FAILED, result.get(OUTCOME).asString());
    }
}
