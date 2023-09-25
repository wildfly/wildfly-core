/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.socketbindinggroups;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.TransformersTestParameterized;
import org.jboss.as.core.model.test.TransformersTestParameterized.TransformersParameter;
import org.jboss.as.core.model.test.util.TransformersTestParameter;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(TransformersTestParameterized.class)
public class SocketBindingGroupTransformersTestCase extends AbstractCoreModelTest {

    private final ModelVersion modelVersion;
    private final ModelTestControllerVersion testControllerVersion;

    @TransformersParameter
    public static List<TransformersTestParameter> parameters(){
        return TransformersTestParameter.setupVersions();
    }

    public SocketBindingGroupTransformersTestCase(TransformersTestParameter params) {
        this.modelVersion = params.getModelVersion();
        this.testControllerVersion = params.getTestControllerVersion();
    }

    @Test
    public void testSocketBindingGroupsTransformer() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource("domain-transform.xml");

        builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());

        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        checkCoreModelTransformation(mainServices, modelVersion);
    }

    @Test
    public void testRejectIncludes() throws Exception {
        if (modelVersion.getMajor() >= 4) {
            return;
        }
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN);
        builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());

        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        //Get the boot operations from the xml file
        List<ModelNode> operations = builder.parseXmlResource("domain.xml");

        //Run the standard tests trying to execute the parsed operations.
        PathAddress addr = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "test-with-includes");
        FailedOperationTransformationConfig config = new FailedOperationTransformationConfig();
        config.addFailedAttribute(addr, new FailedOperationTransformationConfig.NewAttributesConfig(INCLUDES));
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, operations, config);
    }
}
