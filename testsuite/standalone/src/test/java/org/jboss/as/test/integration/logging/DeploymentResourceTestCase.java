/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.logging;

import java.nio.file.Files;
import java.util.LinkedList;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.logging.perdeploy.DeploymentBaseTestCase;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(DeploymentResourceTestCase.LoggingProfileSetup.class)
public class DeploymentResourceTestCase extends AbstractLoggingTestCase {

    private static final String PROFILE_NAME = "test-deployment-profile";
    private static final String PROFILE_LOG_NAME = "test-deployment-profile-file.log";
    private static final String PER_DEPLOY_LOG_NAME = "logging-properties-test.log";
    private static final String RUNTIME_NAME = "logging-test-runtime.jar";

    @After
    public void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDefaultName() throws Exception {
        final JavaArchive deployment = createDeployment();
        deploy(deployment, DEPLOYMENT_NAME);
        final ModelNode loggingConfiguration = readDeploymentResource(DEPLOYMENT_NAME);
        // The address should have logging.properties
        final LinkedList<Property> resultAddress = new LinkedList<>(Operations.getOperationAddress(loggingConfiguration).asPropertyList());
        Assert.assertEquals("The configuration path did not contain default", "default", resultAddress.getLast().getValue().asString());

        final ModelNode handler = loggingConfiguration.get("handler", "FILE");
        Assert.assertTrue("The FILE handler was not found effective configuration", handler.isDefined());
        Assert.assertTrue("The attribute properties was not found on the file handler", handler.hasDefined("properties"));
        String fileName = null;
        // Find the fileName property
        for (Property property : handler.get("properties").asPropertyList()) {
            if ("fileName".equals(property.getName())) {
                fileName = property.getValue().asString();
                break;
            }
        }
        Assert.assertNotNull("fileName property not found", fileName);
        Assert.assertTrue("Expected the file name to end in server.log", fileName.endsWith("server.log"));
    }

    @Test
    public void testRuntimeName() throws Exception {
        final JavaArchive deployment = createDeployment();
        deploy(deployment, RUNTIME_NAME);
        final ModelNode loggingConfiguration = readDeploymentResource(DEPLOYMENT_NAME);
        // The address should have logging.properties
        final LinkedList<Property> resultAddress = new LinkedList<>(Operations.getOperationAddress(loggingConfiguration).asPropertyList());
        Assert.assertEquals("The configuration path did not contain default", "default", resultAddress.getLast().getValue().asString());

        final ModelNode handler = loggingConfiguration.get("handler", "FILE");
        Assert.assertTrue("The FILE handler was not found effective configuration", handler.isDefined());
        Assert.assertTrue("The attribute properties was not found on the file handler", handler.hasDefined("properties"));
        String fileName = null;
        // Find the fileName property
        for (Property property : handler.get("properties").asPropertyList()) {
            if ("fileName".equals(property.getName())) {
                fileName = property.getValue().asString();
                break;
            }
        }
        Assert.assertNotNull("fileName property not found", fileName);
        Assert.assertTrue("Expected the file name to end in server.log", fileName.endsWith("server.log"));
    }

    @Test
    public void testLoggingProfileDefaultName() throws Exception {
        final JavaArchive deployment = createDeployment()
                .addAsResource(new StringAsset("Dependencies: io.undertow.core\nLogging-Profile: " + PROFILE_NAME), "META-INF/MANIFEST.MF");
        deploy(deployment, DEPLOYMENT_NAME);
        final ModelNode loggingConfiguration = readDeploymentResource(DEPLOYMENT_NAME);
        // The address should have logging.properties
        final LinkedList<Property> resultAddress = new LinkedList<>(Operations.getOperationAddress(loggingConfiguration).asPropertyList());
        Assert.assertEquals("The configuration path did not include profile-" + PROFILE_NAME, "profile-" + PROFILE_NAME, resultAddress.getLast().getValue().asString());

        final ModelNode handler = loggingConfiguration.get("handler", "FILE");
        Assert.assertTrue("The FILE handler was not found effective configuration", handler.isDefined());
        Assert.assertTrue("The attribute properties was not found on the file handler", handler.hasDefined("properties"));
        String fileName = null;
        // Find the fileName property
        for (Property property : handler.get("properties").asPropertyList()) {
            if ("fileName".equals(property.getName())) {
                fileName = property.getValue().asString();
                break;
            }
        }
        Assert.assertNotNull("fileName property not found", fileName);
        Assert.assertTrue("Expected the file name to end in server.log", fileName.endsWith(PROFILE_LOG_NAME));
    }

    @Test
    public void testLoggingProfileRuntimeName() throws Exception {
        final JavaArchive deployment = createDeployment()
                .addAsResource(new StringAsset("Dependencies: io.undertow.core\nLogging-Profile: " + PROFILE_NAME), "META-INF/MANIFEST.MF");
        deploy(deployment, RUNTIME_NAME);
        final ModelNode loggingConfiguration = readDeploymentResource(DEPLOYMENT_NAME);
        // The address should have logging.properties
        final LinkedList<Property> resultAddress = new LinkedList<>(Operations.getOperationAddress(loggingConfiguration).asPropertyList());
        Assert.assertEquals("The configuration path did not include profile-" + PROFILE_NAME, "profile-" + PROFILE_NAME, resultAddress.getLast().getValue().asString());

        final ModelNode handler = loggingConfiguration.get("handler", "FILE");
        Assert.assertTrue("The FILE handler was not found effective configuration", handler.isDefined());
        Assert.assertTrue("The attribute properties was not found on the file handler", handler.hasDefined("properties"));
        String fileName = null;
        // Find the fileName property
        for (Property property : handler.get("properties").asPropertyList()) {
            if ("fileName".equals(property.getName())) {
                fileName = property.getValue().asString();
                break;
            }
        }
        Assert.assertNotNull("fileName property not found", fileName);
        Assert.assertTrue("Expected the file name to end in server.log", fileName.endsWith(PROFILE_LOG_NAME));
    }

    @Test
    public void testPerDeploymentDefaultName() throws Exception {
        final JavaArchive deployment = createDeployment()
                .addAsResource(DeploymentBaseTestCase.class.getPackage(), "logging.properties", "META-INF/logging.properties");
        deploy(deployment, DEPLOYMENT_NAME);
        final ModelNode loggingConfiguration = readDeploymentResource(DEPLOYMENT_NAME);
        // The address should have logging.properties
        final LinkedList<Property> resultAddress = new LinkedList<>(Operations.getOperationAddress(loggingConfiguration).asPropertyList());
        Assert.assertTrue("The configuration path did not include logging.properties", resultAddress.getLast().getValue().asString().contains("logging.properties"));

        final ModelNode handler = loggingConfiguration.get("handler", "FILE");
        Assert.assertTrue("The FILE handler was not found effective configuration", handler.isDefined());
        Assert.assertTrue("The attribute properties was not found on the file handler", handler.hasDefined("properties"));
        String fileName = null;
        // Find the fileName property
        for (Property property : handler.get("properties").asPropertyList()) {
            if ("fileName".equals(property.getName())) {
                fileName = property.getValue().asString();
                break;
            }
        }
        Assert.assertNotNull("fileName property not found", fileName);
        Assert.assertTrue(String.format("Expected the file name to end in %s but was %s", PER_DEPLOY_LOG_NAME, fileName), fileName.endsWith(PER_DEPLOY_LOG_NAME));
    }

    @Test
    public void testPerDeploymentRuntimeName() throws Exception {
        final JavaArchive deployment = createDeployment()
                .addAsResource(DeploymentBaseTestCase.class.getPackage(), "logging.properties", "META-INF/logging.properties");
        deploy(deployment, RUNTIME_NAME);
        final ModelNode loggingConfiguration = readDeploymentResource(DEPLOYMENT_NAME);
        // The address should have logging.properties
        final LinkedList<Property> resultAddress = new LinkedList<>(Operations.getOperationAddress(loggingConfiguration).asPropertyList());
        Assert.assertTrue("The configuration path did not include logging.properties", resultAddress.getLast().getValue().asString().contains("logging.properties"));

        final ModelNode handler = loggingConfiguration.get("handler", "FILE");
        Assert.assertTrue("The FILE handler was not found effective configuration", handler.isDefined());
        Assert.assertTrue("Properties were not defined on the FILE handler", handler.hasDefined("properties"));
        String fileName = null;
        // Find the fileName property
        for (Property property : handler.get("properties").asPropertyList()) {
            if ("fileName".equals(property.getName())) {
                fileName = property.getValue().asString();
                break;
            }
        }
        Assert.assertNotNull("fileName property not found", fileName);
        Assert.assertTrue(String.format("Expected the file name to end in %s but was %s", PER_DEPLOY_LOG_NAME, fileName), fileName.endsWith(PER_DEPLOY_LOG_NAME));
    }

    static class LoggingProfileSetup extends ServerReload.SetupTask {

        @Override
        public void setup(ManagementClient managementClient) throws Exception {

            final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create();

            // create dummy-profile1
            builder.addStep(Operations.createAddOperation(createAddress("logging-profile", PROFILE_NAME)));

            // add file handler
            ModelNode op = Operations.createAddOperation(createAddress("logging-profile", PROFILE_NAME, "periodic-rotating-file-handler", "FILE"));
            op.get("level").set("FATAL");
            op.get("append").set("true");
            op.get("suffix").set(".yyyy-MM-dd");
            final ModelNode file = op.get("file").setEmptyObject();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(PROFILE_LOG_NAME);
            op.get("file").set(file);
            op.get("formatter").set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");
            builder.addStep(op);

            executeOperation(builder.build());
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {

            executeOperation(Operations.createRemoveOperation(createAddress("logging-profile", PROFILE_NAME)));

            // Delete log files only if this did not fail
            Files.deleteIfExists(getAbsoluteLogFilePath(PROFILE_LOG_NAME));

            super.tearDown(client);
        }
    }
}
