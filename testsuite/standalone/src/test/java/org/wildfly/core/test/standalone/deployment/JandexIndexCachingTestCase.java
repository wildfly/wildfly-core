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
package org.wildfly.core.test.standalone.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.PropertyPermission;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.logging.LoggingUtil;
import org.jboss.as.test.shared.logging.TestLogHandlerSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceName;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@org.wildfly.core.testrunner.ServerSetup(JandexIndexCachingTestCase.LogHandlerSetup.class)
public class JandexIndexCachingTestCase {

    private static final String DEPLOYMENT_NAME_SUFFIX = "-" + JandexIndexCachingTestCase.class.getSimpleName() + ".jar";
    private static final String HANDLER_NAME = JandexIndexCachingTestCase.class.getSimpleName();
    private static final String DEPENDEE_MODULE = "org.wildfly.common";
    private static final String INDEXING_MSG = "Creating annotation index for static module " + DEPENDEE_MODULE;

    @Inject
    private ManagementClient managementClient;

    @Test
    public void test() throws Exception {

        ModelControllerClient mcc = managementClient.getControllerClient();

        // Create 2 deployments and operations to deploy them
        JavaArchive deploymentA = prepareTestDeployment("appA", ServiceActivatorDeploymentA.class);
        JavaArchive deploymentB = prepareTestDeployment("appB", ServiceActivatorDeploymentB.class);
        Operation deploymentAop = createDeploymentOp(deploymentA);
        Operation deploymentBop = createDeploymentOp(deploymentB);
        // Execute individually
        ManagementOperations.executeOperation(mcc, deploymentAop);
        assertDeploymentFunctional(deploymentA.getName());
        ManagementOperations.executeOperation(mcc, deploymentBop);
        assertDeploymentFunctional(deploymentB.getName());

        // Should be two log messages
        Assert.assertEquals(2, LoggingUtil.countLogMessage(mcc, HANDLER_NAME, INDEXING_MSG));

        // Remove
        ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deploymentA.getName())));
        ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deploymentB.getName())));

        // Deploy again in a single composite operation
        ModelNode composite = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get("steps");
        steps.add(deploymentAop.getOperation());
        ModelNode bOp = deploymentBop.getOperation();
        // Change bOp's index
        bOp.get("content").get(0).get("input-stream-index").set(1);
        steps.add(bOp);

        List<InputStream> streams = new ArrayList<>();
        streams.add(deploymentA.as(ZipExporter.class).exportAsInputStream());
        streams.add(deploymentB.as(ZipExporter.class).exportAsInputStream());

        ManagementOperations.executeOperation(mcc, Operation.Factory.create(composite, streams, true));
        assertDeploymentFunctional(deploymentA.getName());
        assertDeploymentFunctional(deploymentB.getName());

        // Index caching should mean only one log message should have been added
        Assert.assertEquals(3, LoggingUtil.countLogMessage(mcc, HANDLER_NAME, INDEXING_MSG));

        // Reload
        ServerReload.executeReloadAndWaitForCompletion(mcc);

        // Only one log message should have been added
        Assert.assertEquals(4, LoggingUtil.countLogMessage(mcc, HANDLER_NAME, INDEXING_MSG));
    }

    @After
    public void cleanUp() throws IOException {
        managementClient.getControllerClient().execute(Util.createRemoveOperation(PathAddress.pathAddress("deployment", "appA" + DEPLOYMENT_NAME_SUFFIX)));
        managementClient.getControllerClient().execute(Util.createRemoveOperation(PathAddress.pathAddress("deployment", "appB" + DEPLOYMENT_NAME_SUFFIX)));
    }

    private Operation createDeploymentOp(JavaArchive deployment) {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(deployment.as(ZipExporter.class).exportAsInputStream());
        final ModelNode addOperation = Util.createAddOperation(PathAddress.pathAddress("deployment", deployment.getName()));
        addOperation.get("enabled").set(true);
        addOperation.get("content").add().get("input-stream-index").set(0);
        return Operation.Factory.create(addOperation, streams, true);
    }

    private JavaArchive prepareTestDeployment(String deploymentName, Class<?> clazz) throws IOException {
        deploymentName = deploymentName + DEPLOYMENT_NAME_SUFFIX;

        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, deploymentName);
        archive.addClass(clazz);
        archive.addClass(ServiceActivatorDeployment.class);
        archive.addAsServiceProvider(ServiceActivator.class, clazz);
        archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc\n"), "MANIFEST.MF");
        String propFileContent = deploymentName + "Service=started" + '\n';
        archive.addAsResource(new StringAsset(propFileContent), ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission(deploymentName + "Service", "write"),
                new PropertyPermission("service", "write")
        ), "permissions.xml");
        archive.addAsResource(new StringAsset(prepareJBossDeploymentStructure()),
                "META-INF/jboss-deployment-structure.xml");
        return archive;
    }

    private String prepareJBossDeploymentStructure() {
        // Declare a dep on a module and request annotations info from it
        return "<jboss-deployment-structure>\n"
                + "  <deployment>\n"
                + "    <dependencies>\n"
                + "      <module name=\"" + DEPENDEE_MODULE + "\" annotations=\"true\"/>\n"
                + "    </dependencies>\n"
                + "  </deployment>\n"
                + "</jboss-deployment-structure>\n";
    }

    private void assertDeploymentFunctional(String deploymentName) throws IOException, MgmtOperationException {
        final PathAddress RESOURCE_ADDRESS = PathAddress.pathAddress(
                PathElement.pathElement("core-service", "platform-mbean"),
                PathElement.pathElement(ModelDescriptionConstants.TYPE, "runtime")
        );
        final ModelNode op = Util.createEmptyOperation(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION, RESOURCE_ADDRESS);
        op.get(ModelDescriptionConstants.NAME).set("system-properties");
        ModelNode serviceActivatorValue = ManagementOperations.executeOperation(managementClient.getControllerClient(), op).get(deploymentName + "Service");
        Assert.assertEquals("started", serviceActivatorValue.asString());
    }

    public static class ServiceActivatorDeploymentA extends ServiceActivatorDeployment {

        public ServiceActivatorDeploymentA() {
            super(ServiceName.of(ServiceActivatorDeployment.class.getSimpleName(), "a"), ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        }
    }

    public static class ServiceActivatorDeploymentB extends ServiceActivatorDeployment {

        public ServiceActivatorDeploymentB() {
            super(ServiceName.of(ServiceActivatorDeployment.class.getSimpleName(), "b"), ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        }
    }

    public static final class LogHandlerSetup extends TestLogHandlerSetupTask {

        @Override
        public Collection<String> getCategories() {
            return Collections.singleton("org.jboss.as.server.deployment");
        }

        @Override
        public String getLevel() {
            return "DEBUG";
        }

        @Override
        public String getHandlerName() {
            return HANDLER_NAME;
        }

        @Override
        public String getLogFileName() {
            return HANDLER_NAME + ".log";
        }
    }
}
