/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.expressions;

import static org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension.ATTR;
import static org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension.MODULE_NAME;
import static org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension.OUTPUT_PROPERTY;
import static org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension.PARAM_EXPRESSION;
import static org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension.PARAM_SYS_PROP;
import static org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension.PATH;
import static org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension.READ_SYS_PROP;
import static org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension.RESOLVE;

import java.io.File;
import jakarta.inject.Inject;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.manualmode.expressions.module.TestSecureExpressionsExtension;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.as.test.shared.SecureExpressionUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerController;
import org.xnio.IoUtils;

/**
 * Abstract base class for tests of secure expression handling. Can be used for different kinds of secure expressions.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry
 */
abstract class AbstractSecureExpressionsTestCase {

    public static final String CLEAR_TEXT = "123_Testing_Stuff_thing";
    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(PATH);

    private static SecureExpressionUtil.SecureExpressionData EXPRESSION_DATA = null;

    @Inject
    private static ServerController containerController;

    private static TestModule testModule;

    /**
     * Exercise OperationContext expression resolution via the test subsystem's custom 'resolve' op,
     * the OSH for which uses the OperationContext to resolve a expression.
     */
    @Test
    public void testOperationContextResolution() throws Exception {
        ManagementClient client = containerController.getClient();

        ModelNode op = createResolveExpressionOp(EXPRESSION_DATA.getExpression());
        ModelNode result = client.executeForResult(op);
        Assert.assertEquals(result.toString(), CLEAR_TEXT, result.asString());

        op = createResolveExpressionOp(getInvalidExpression());
        ModelTestUtils.checkFailed(client.getControllerClient().execute(op));
    }

    /**
     * Exercise deployment expression resolution via the test subsystem's custom DUP,
     * which accesses the DeploymentUnit expression resolution facility, resolves a
     * known expression, and sets a system property to the resolved value. The
     * test subsystem resource provides a custom OSH that will return the system property value.
     */
    @Test
    public void testDeploymentResolution() throws Exception {
        ManagementClient client = containerController.getClient();

        ModelNode op = Util.createOperation(READ_SYS_PROP.getName(), SUBSYSTEM_ADDRESS);
        op.get(PARAM_SYS_PROP.getName()).set(OUTPUT_PROPERTY);
        op.protect();

        // Confirm correct initial system state
        ModelNode result = client.executeForResult(op);
        Assert.assertFalse(result.toString(), result.isDefined());

        // The real test. Deploy to trigger resolution and the setting of the property, then check the property
        String deploymentName = getClass().getSimpleName() + ".jar";
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, deploymentName)
                .add(new StringAsset("empty"), "empty.txt");
        containerController.deploy(archive, deploymentName);
        try {
            result = client.executeForResult(op);
            Assert.assertEquals(CLEAR_TEXT, result.asString());
        } finally {
            containerController.undeploy(deploymentName);
        }

        // Confirm we cleaned up the property. This isn't a test of
        // production code, just a check on test tidiness
        result = client.executeForResult(op);
        Assert.assertFalse(result.toString(), result.isDefined());
    }

    /**
     * Validate that the top-level 'resolve-expression' op and the 'resolve-expressions' param to the
     * standard read-resource and read-attribute ops don't result in returning resolved secure expression
     * values to a remote client.
     */
    @Test
    public void testStandardRemoteExpressionResolution() throws Exception {
        ModelControllerClient client = containerController.getClient().getControllerClient();

        String secureExpression = EXPRESSION_DATA.getExpression();

        ModelNode op = Util.createEmptyOperation("resolve-expression", PathAddress.EMPTY_ADDRESS);
        op.get(PARAM_EXPRESSION.getName()).set(secureExpression);
        ModelNode response = client.execute(op);
        Assert.assertEquals(response.toString(), secureExpression, response.get("result").asString());
        String responseHeaders = response.get("response-headers").asString();
        Assert.assertTrue(response.toString(), responseHeaders.contains(secureExpression));
        Assert.assertTrue(response.toString(), responseHeaders.contains("WFLYCTL0480"));

        // First confirm a non-resolving read-attribute is as expected
        op = Util.getReadAttributeOperation(SUBSYSTEM_ADDRESS, ATTR.getName());
        response = client.execute(op);
        ModelNode attrValue = response.get("result");
        Assert.assertEquals(response.toString(), ModelType.EXPRESSION, attrValue.getType());
        Assert.assertEquals(response.toString(), secureExpression, attrValue.asString());

        // Now a resolving read
        op.get("resolve-expressions").set(true);
        response = client.execute(op);
        attrValue = response.get("result");
        Assert.assertEquals(response.toString(), ModelType.EXPRESSION, attrValue.getType());
        Assert.assertEquals(response.toString(), secureExpression, attrValue.asString());
        responseHeaders = response.get("response-headers").asString();
        Assert.assertTrue(response.toString(), responseHeaders.contains(secureExpression));
        Assert.assertTrue(response.toString(), responseHeaders.contains("WFLYCTL0479"));

        // First confirm a non-resolving read-resource is as expected
        op = Util.createEmptyOperation("read-resource", SUBSYSTEM_ADDRESS);
        response = client.execute(op);
        attrValue = response.get("result", ATTR.getName());
        Assert.assertEquals(response.toString(), ModelType.EXPRESSION, attrValue.getType());
        Assert.assertEquals(response.toString(), secureExpression, attrValue.asString());

        // Now a resolving read
        op.get("resolve-expressions").set(true);
        response = client.execute(op);
        attrValue = response.get("result", ATTR.getName());
        Assert.assertEquals(response.toString(), ModelType.EXPRESSION, attrValue.getType());
        Assert.assertEquals(response.toString(), secureExpression, attrValue.asString());
        // TODO uncomment when WFCORE-5305 is resolved
        //responseHeaders = response.get("response-headers").asString();
        //Assert.assertTrue(response.toString(), responseHeaders.contains("${VAULT::Testing::Stuff::thing}"));
        //Assert.assertTrue(response.toString(), responseHeaders.contains("WFLYCTL0479"));
    }

    static void tearDownServer(ExceptionFunction<ManagementClient, Void, Exception> securityTeardown) throws Exception {
        ModelControllerClient client = null;
        try {
            ManagementClient managementClient = containerController.getClient();
            client = managementClient.getControllerClient();

            ModelNode subsystemResult = client.execute(Util.createRemoveOperation(SUBSYSTEM_ADDRESS));
            ModelNode extensionResult = client.execute(Util.createRemoveOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME)));

            securityTeardown.apply(managementClient);

            ModelTestUtils.checkOutcome(subsystemResult);
            ModelTestUtils.checkOutcome(extensionResult);

        } finally {
            containerController.stop();
            testModule.remove();
            IoUtils.safeClose(client);
        }
        containerController.stop();
    }

    private static void createTestModule(Class<?>... additionalModuleClasses) throws Exception {
        File moduleXml = new File(TestSecureExpressionsExtension.class.getResource("test-secure-expressions-module.xml").toURI());
        testModule = new TestModule(MODULE_NAME, moduleXml);

        JavaArchive jar = testModule.addResource("test-secure-expressions.jar");
        jar.addClass(TestSecureExpressionsExtension.class)
                .addClass(TestSecureExpressionsExtension.Parser.class)
                .addClass(TestSecureExpressionsExtension.ResourceDescription.class)
                .addClass(TestSecureExpressionsExtension.AddHandler.class)
                .addClass(TestSecureExpressionsExtension.ResolveHandler.class)
                .addClass(TestSecureExpressionsExtension.Processor.class)
                .addAsServiceProvider(Extension.class, TestSecureExpressionsExtension.class);

        if (additionalModuleClasses != null) {
            for (Class<?> additional : additionalModuleClasses) {
                jar.addClass(additional);
            }
        }

        testModule.create(true);
    }

    static void setupServerWithSecureExpressions(@SuppressWarnings("SameParameterValue") SecureExpressionUtil.SecureExpressionData expressionData,
                                                 ExceptionFunction<ManagementClient, Void, Exception> securitySetup) throws Exception {

        EXPRESSION_DATA = expressionData;

        createTestModule();

        containerController.start();
        ManagementClient managementClient = containerController.getClient();

        securitySetup.apply(managementClient);

        ModelControllerClient client = managementClient.getControllerClient();

        //Add the extension
        final ModelNode addExtension = Util.createAddOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME));
        ModelTestUtils.checkOutcome(client.execute(addExtension));

        final ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(PATH));
        addSubsystem.get(ATTR.getName()).set(new ValueExpression(EXPRESSION_DATA.getExpression()));
        ModelTestUtils.checkOutcome(client.execute(addSubsystem));

        ServerReload.executeReloadAndWaitForCompletion(client);
    }

    private ModelNode createResolveExpressionOp(String expression) {
        ModelNode op = Util.createOperation(RESOLVE.getName(), SUBSYSTEM_ADDRESS);
        op.get(PARAM_EXPRESSION.getName()).set(new ValueExpression(expression));
        return op;
    }

    abstract String getInvalidExpression();
}
