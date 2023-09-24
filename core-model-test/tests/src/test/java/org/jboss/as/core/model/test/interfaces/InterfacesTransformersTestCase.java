/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.interfaces;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.TransformersTestParameterized;
import org.jboss.as.core.model.test.TransformersTestParameterized.TransformersParameter;
import org.jboss.as.core.model.test.util.TransformersTestParameter;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(TransformersTestParameterized.class)
public class InterfacesTransformersTestCase extends AbstractCoreModelTest {
    private final ModelVersion modelVersion;
    private final ModelTestControllerVersion testControllerVersion;

    @TransformersParameter
    public static List<TransformersTestParameter> parameters(){
        return TransformersTestParameter.setupVersions();
    }

    public InterfacesTransformersTestCase(TransformersTestParameter params) {
        this.modelVersion = params.getModelVersion();
        this.testControllerVersion = params.getTestControllerVersion();
    }

    @Test
    public void testInterfacesTransformer() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource("domain.xml");

        builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());

        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        checkCoreModelTransformation(mainServices, modelVersion);
    }
}
