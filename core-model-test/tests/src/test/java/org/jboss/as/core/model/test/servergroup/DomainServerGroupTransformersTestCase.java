/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.core.model.test.servergroup;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TIMEOUT;
import static org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition.MANAGEMENT_SUBSYSTEM_ENDPOINT;
import static org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE;
import static org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition.SOCKET_BINDING_PORT_OFFSET;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.LegacyKernelServicesInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.TransformersTestParameterized;
import org.jboss.as.core.model.test.TransformersTestParameterized.TransformersParameter;
import org.jboss.as.core.model.test.util.StandardServerGroupInitializers;
import org.jboss.as.core.model.test.util.TransformersTestParameter;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests transformation of server-groups.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
@RunWith(TransformersTestParameterized.class)
public class DomainServerGroupTransformersTestCase extends AbstractCoreModelTest {

    private final ModelVersion modelVersion;
    private final ModelTestControllerVersion testControllerVersion;

    @TransformersParameter
    public static List<TransformersTestParameter> parameters() {
        return TransformersTestParameter.setupVersions();
    }

    public DomainServerGroupTransformersTestCase(TransformersTestParameter params) {
        this.modelVersion = params.getModelVersion();
        this.testControllerVersion = params.getTestControllerVersion();
    }

    @Test
    public void testServerGroupsTransformer() throws Exception {

        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .createContentRepositoryContent("12345678901234567890")
                .createContentRepositoryContent("09876543210987654321")
                .setXmlResource("servergroup-with-expressions.xml");

        LegacyKernelServicesInitializer legacyInitializer =
                StandardServerGroupInitializers.addServerGroupInitializers(builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion));

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());

        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        checkCoreModelTransformation(mainServices, modelVersion, MODEL_FIXER, MODEL_FIXER);

    }


    @Test
    public void testRejectTransformersEAP() throws Exception {
        if (modelVersion.getMajor() > 1) {
            return;
        }
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .createContentRepositoryContent("12345678901234567890")
                .createContentRepositoryContent("09876543210987654321");

        // Add legacy subsystems
        StandardServerGroupInitializers.addServerGroupInitializers(builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion));
        KernelServices mainServices = builder.build();

        PathAddress serverGroupAddress = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP));
        List<ModelNode> ops = builder.parseXmlResource("servergroup.xml");
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, ops, new FailedOperationTransformationConfig()
                .addFailedAttribute(serverGroupAddress,
                        FailedOperationTransformationConfig.ChainedConfig.createBuilder(MANAGEMENT_SUBSYSTEM_ENDPOINT, SOCKET_BINDING_PORT_OFFSET, SOCKET_BINDING_DEFAULT_INTERFACE)
                                .addConfig(new FailedOperationTransformationConfig.RejectExpressionsConfig(MANAGEMENT_SUBSYSTEM_ENDPOINT, SOCKET_BINDING_PORT_OFFSET))
                                .addConfig(new FailedOperationTransformationConfig.NewAttributesConfig(SOCKET_BINDING_DEFAULT_INTERFACE))
                                .build().setReadOnly(MANAGEMENT_SUBSYSTEM_ENDPOINT)));

        //check that we reject /server-group=main-server-group:suspend-servers(timeout=?)
        OperationTransformer.TransformedOperation transOp = mainServices.transformOperation(modelVersion, Util.createOperation("suspend-servers", serverGroupAddress));
        Assert.assertTrue(transOp.getFailureDescription(), transOp.rejectOperation(success()));

        //check that we reject /server-group=main-server-group:resume-servers()
        transOp = mainServices.transformOperation(modelVersion, Util.createOperation("resume-servers", serverGroupAddress));
        Assert.assertNull("operation should have been discarded", transOp.getTransformedOperation());

        // check that we reject new attribute /server-group=main-server-group:stop-servers(blocking=true, timeout=?) (new timeout attribute)
        ModelNode stopServerOp = Util.createOperation("stop-servers", serverGroupAddress);
        stopServerOp.get(TIMEOUT).set(1000);
        transOp = mainServices.transformOperation(modelVersion, stopServerOp);
        Assert.assertTrue(transOp.getFailureDescription(), transOp.rejectOperation(success()));
        stopServerOp.remove(TIMEOUT);
        transOp = mainServices.transformOperation(modelVersion, stopServerOp); //this operation shouldn't be rejected
        Assert.assertFalse(transOp.getFailureDescription(), transOp.rejectOperation(success()));
    }

    private static ModelNode success() {
         final ModelNode result = new ModelNode();
         result.get(ModelDescriptionConstants.OUTCOME).set(ModelDescriptionConstants.SUCCESS);
         result.get(ModelDescriptionConstants.RESULT);
         return result;
     }

    @Test
    public void testRejectTransformers9x() throws Exception {
        if (modelVersion.getMajor() <= 1 || modelVersion.getMinor() <= 3) {
            return;
        }
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .createContentRepositoryContent("12345678901234567890")
                .createContentRepositoryContent("09876543210987654321");

        // Add legacy subsystems
        LegacyKernelServicesInitializer legacyInitializer =
                StandardServerGroupInitializers.addServerGroupInitializers(builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion));
        KernelServices mainServices = builder.build();

        List<ModelNode> ops = builder.parseXmlResource("servergroup.xml");
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, ops, new FailedOperationTransformationConfig()
                .addFailedAttribute(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP)),
                        FailedOperationTransformationConfig.ChainedConfig.createBuilder(
                                MANAGEMENT_SUBSYSTEM_ENDPOINT, SOCKET_BINDING_PORT_OFFSET, SOCKET_BINDING_DEFAULT_INTERFACE)
                                .addConfig(new FailedOperationTransformationConfig.RejectExpressionsConfig(MANAGEMENT_SUBSYSTEM_ENDPOINT,
                                        SOCKET_BINDING_PORT_OFFSET))
                                .addConfig(new FailedOperationTransformationConfig.NewAttributesConfig(SOCKET_BINDING_DEFAULT_INTERFACE))
                                .build().setReadOnly(MANAGEMENT_SUBSYSTEM_ENDPOINT)));

    }

    @Test
    public void testRejectKillDestroyTransformers() throws Exception {
        if (modelVersion.getMajor() > 5) {
            return;
        }
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .createContentRepositoryContent("12345678901234567890")
                .createContentRepositoryContent("09876543210987654321");

        // Add legacy subsystems
        StandardServerGroupInitializers.addServerGroupInitializers(builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion));
        KernelServices mainServices = builder.build();

        PathAddress serverGroupAddress = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP));


        OperationTransformer.TransformedOperation transOp;

        //check that we reject /server-group=main-server-group:kill-servers()
        ModelNode killServersOp = Util.createOperation("kill-servers", serverGroupAddress);
        transOp = mainServices.transformOperation(modelVersion, killServersOp);
        Assert.assertTrue(transOp.getFailureDescription(), transOp.rejectOperation(success()));

        //check that we reject /server-group=main-server-group:destroy-servers()
        ModelNode destroyServersOp = Util.createOperation("destroy-servers", serverGroupAddress);
        transOp = mainServices.transformOperation(modelVersion, destroyServersOp);
        Assert.assertTrue(transOp.getFailureDescription(), transOp.rejectOperation(success()));
    }

    private static final ModelFixer MODEL_FIXER = new ModelFixer() {

        @Override
        public ModelNode fixModel(ModelNode modelNode) {
            modelNode.remove(SOCKET_BINDING_GROUP);
            modelNode.remove(PROFILE);
            return modelNode;
        }
    };
}
