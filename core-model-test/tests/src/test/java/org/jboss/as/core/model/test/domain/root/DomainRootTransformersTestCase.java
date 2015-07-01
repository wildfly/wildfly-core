/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
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

package org.jboss.as.core.model.test.domain.root;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TIMEOUT;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.TransformersTestParameterized;
import org.jboss.as.core.model.test.TransformersTestParameterized.TransformersParameter;
import org.jboss.as.core.model.test.util.StandardServerGroupInitializers;
import org.jboss.as.core.model.test.util.TransformersTestParameter;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests transformation of server-groups.
 *
 * @author Tomaz Cerar
 */
@RunWith(TransformersTestParameterized.class)
public class DomainRootTransformersTestCase extends AbstractCoreModelTest {

    private final ModelVersion modelVersion;
    private final ModelTestControllerVersion testControllerVersion;

    @TransformersParameter
    public static List<TransformersTestParameter> parameters() {
        return TransformersTestParameter.setupVersions();
    }

    public DomainRootTransformersTestCase(TransformersTestParameter params) {
        this.modelVersion = params.getModelVersion();
        this.testControllerVersion = params.getTestControllerVersion();
    }


    @Test
    public void testRejectTransformersEAP() throws Exception {
        if (modelVersion.getMajor() > 1) {
            return;
        }
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER);


        // Add legacy subsystems
        StandardServerGroupInitializers.addServerGroupInitializers(builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion));
        KernelServices mainServices = builder.build();

        PathAddress address = PathAddress.EMPTY_ADDRESS;
        //List<ModelNode> ops = builder.parseXmlResource("domain-root.xml");

        //check that we reject /:suspend-servers(timeout=?)
        OperationTransformer.TransformedOperation transOp = mainServices.transformOperation(modelVersion, Util.createOperation("suspend-servers", address));
        Assert.assertTrue(transOp.getFailureDescription(), transOp.rejectOperation(success()));

        //check that we reject /:resume-servers()
        transOp = mainServices.transformOperation(modelVersion, Util.createOperation("resume-servers", address));
        Assert.assertNull("operation should have been discarded", transOp.getTransformedOperation());

        // check that we reject new attribute /:stop-servers(blocking=true, timeout=?) (new timeout attribute)
        ModelNode stopServerOp = Util.createOperation("stop-servers", address);
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

}
