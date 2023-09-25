/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.core.model.test.domain.root;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NORMAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_MODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TIMEOUT;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

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

    @Test
    public void testRejectStartMode() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER);

        StandardServerGroupInitializers.addServerGroupInitializers(builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion));
        KernelServices mainServices = builder.build();

        PathAddress address = PathAddress.EMPTY_ADDRESS;

        OperationTransformer.TransformedOperation transOp;

        if (modelVersion.getMajor() > 4 || modelVersion.getMajor() == 4 && modelVersion.getMinor() != 0) {
            for (String opName : Arrays.asList(START_SERVERS, RELOAD_SERVERS, RESTART_SERVERS)) {
                ModelNode op = Util.createOperation(opName, address);
                op.get(START_MODE).set(SUSPEND);
                transOp = mainServices.transformOperation(modelVersion, op);

                Assert.assertTrue(String.format("The operation %s with 'start-mode => suspend' must not be rejected for the version %s. Failure description is %s", opName, modelVersion, transOp.getFailureDescription()),
                        !transOp.rejectOperation(success()));

                op = Util.createOperation(opName, address);
                op.get(START_MODE).set(NORMAL);
                transOp = mainServices.transformOperation(modelVersion, op);

                Assert.assertTrue(String.format("The operation %s with 'start-mode => normal' must have defined the start-mode attribute for the version %s. Failure description is %s", opName, modelVersion, transOp.getFailureDescription()),
                        !transOp.rejectOperation(success()) && transOp.getTransformedOperation().hasDefined(START_MODE));
            }
        } else {
            for (String opName : Arrays.asList(START_SERVERS, RELOAD_SERVERS, RESTART_SERVERS)) {
                ModelNode op = Util.createOperation(opName, address);
                op.get(START_MODE).set(SUSPEND);
                transOp = mainServices.transformOperation(modelVersion, op);

                Assert.assertTrue(String.format("The operation %s with 'start-mode => suspend' must be rejected for the version %s. Failure description is %s", opName, modelVersion, transOp.getFailureDescription()),
                        transOp.rejectOperation(success()));

                op = Util.createOperation(opName, address);
                op.get(START_MODE).set(NORMAL);
                transOp = mainServices.transformOperation(modelVersion, op);

                Assert.assertTrue(String.format("The operation %s with 'start-mode => normal' must have undefined the start-mode attribute for the version %s. Failure description is %s", opName, modelVersion, transOp.getFailureDescription()),
                        !transOp.rejectOperation(success()) && !transOp.getTransformedOperation().hasDefined(START_MODE));
            }
        }
    }

    @Test
    public void testRenameTimeoutToSuspendTimeoutTransformers() throws Exception {
        if (modelVersion.getMajor() > 8) {
            return;
        }

        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .createContentRepositoryContent("12345678901234567890")
                .createContentRepositoryContent("09876543210987654321");

        StandardServerGroupInitializers.addServerGroupInitializers(builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion));
        KernelServices mainServices = builder.build();

        PathAddress address = PathAddress.EMPTY_ADDRESS;

        OperationTransformer.TransformedOperation transOp;
        Predicate<ModelNode> renameValidation = m -> m.hasDefined(TIMEOUT) && !m.hasDefined(SUSPEND_TIMEOUT) && m.get(TIMEOUT).asInt() == 10;

        //suspend-servers operation is undefined for versions <=1
        if (modelVersion.getMajor() > 1) {
            ModelNode suspendServers = Util.createOperation(SUSPEND_SERVERS, address);
            suspendServers.get(SUSPEND_TIMEOUT).set(10);
            transOp = mainServices.transformOperation(modelVersion, suspendServers);

            Assert.assertTrue(String.format("Attribute suspend-timeout for %s operation was not renamed to timeout for version %s. Failure description is %s", SUSPEND_SERVERS, modelVersion, transOp.getFailureDescription()),
                    renameValidation.test(transOp.getTransformedOperation()));
        }

        ModelNode stopServers = Util.createOperation(STOP_SERVERS, address);
        stopServers.get(SUSPEND_TIMEOUT).set(10);
        transOp = mainServices.transformOperation(modelVersion, stopServers);

        Assert.assertTrue(String.format("Attribute suspend-timeout for %s operation was not renamed to timeout for version %s. Failure description is %s", STOP_SERVERS, modelVersion, transOp.getFailureDescription()),
                renameValidation.test(transOp.getTransformedOperation()));

        if (modelVersion.getMajor() <= 1) {
            Assert.assertTrue(String.format("Operation %s with suspend-timeout renamed to timeout must be rejected for version %s. Failure description is %s", STOP_SERVERS, modelVersion, transOp.getFailureDescription()),
                    transOp.rejectOperation(success()));
        } else {
            Assert.assertTrue(String.format("Operation %s with suspend-timeout renamed to timeout must not be rejected for version %s. Failure description is %s", STOP_SERVERS, modelVersion, transOp.getFailureDescription()),
                    !transOp.rejectOperation(success()));
        }
    }

    private static ModelNode success() {
        final ModelNode result = new ModelNode();
        result.get(ModelDescriptionConstants.OUTCOME).set(ModelDescriptionConstants.SUCCESS);
        result.get(ModelDescriptionConstants.RESULT);
        return result;
    }

}
