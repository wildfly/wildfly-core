/*
* Copyright 2021 Red Hat, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.wildfly.core.test.standalone.secmgr;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.undertow.UndertowServiceActivator;

/**
 * <p>Class that configures a simple undertow handler that reads the <em>java.version</em>
 * system property and prints it in the response. The idea is adding an invalid
 * permission in each section (maximum and minimum set in the configuration and
 * inside the deployment file). The class also checks the warnings are displayed
 * in the log file for each section.</p>
 *
 * @author rmartinc
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup(InvalidPermissionTestCase.Setup.class)
public class InvalidPermissionTestCase {

    private static final String DEPLOY_NAME = InvalidPermissionTestCase.class.getSimpleName() + ".war";
    private static final String HANDLER_NAME = "customFileAppender";
    private static final String FILE_NAME = "invalid-permission-file.log";

    private static Path logFile;

    static class Setup implements ServerSetupTask {

        private ModelNode maxPermissions;
        private ModelNode minPermissions;

        private static ModelNode readPermissions(ManagementClient client, String attribute) throws IOException {
            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
            op.get(ModelDescriptionConstants.OP_ADDR).add(ModelDescriptionConstants.SUBSYSTEM, "security-manager");
            op.get(ModelDescriptionConstants.OP_ADDR).add("deployment-permissions", "default");
            op.get(ModelDescriptionConstants.NAME).set(attribute);
            ModelNode result = client.getControllerClient().execute(op);
            assertSuccessResult(result);
            return result.get(ModelDescriptionConstants.RESULT);
        }

        private static void writePermissions(ManagementClient client, String attribute, ModelNode... permissions) throws IOException {
            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
            op.get(ModelDescriptionConstants.OP_ADDR).add(ModelDescriptionConstants.SUBSYSTEM, "security-manager");
            op.get(ModelDescriptionConstants.OP_ADDR).add("deployment-permissions", "default");
            op.get(ModelDescriptionConstants.NAME).set(attribute);
            op.get(ModelDescriptionConstants.VALUE).setEmptyList();
            for (ModelNode permission : permissions) {
                op.get(ModelDescriptionConstants.VALUE).add(permission);
            }
            ModelNode result = client.getControllerClient().execute(op);
            assertSuccessResult(result);
        }

        private static void removePermissions(ManagementClient client, String attribute) throws IOException {
            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION);
            op.get(ModelDescriptionConstants.OP_ADDR).add(ModelDescriptionConstants.SUBSYSTEM, "security-manager");
            op.get(ModelDescriptionConstants.OP_ADDR).add("deployment-permissions", "default");
            op.get(ModelDescriptionConstants.NAME).set(attribute);
            ModelNode result = client.getControllerClient().execute(op);
            assertSuccessResult(result);
        }

        private static void createAndAssignCustomHandler(ManagementClient client) throws IOException {
            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP_ADDR).add(ModelDescriptionConstants.SUBSYSTEM, "logging");
            op.get(ModelDescriptionConstants.OP_ADDR).add("file-handler", HANDLER_NAME);
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
            op.get("level").set("WARN");
            final ModelNode file = op.get("file").setEmptyObject();
            file.get("path").set(FILE_NAME);
            file.get("relative-to").set("jboss.server.log.dir");
            ModelNode result = client.getControllerClient().execute(op);
            assertSuccessResult(result);

            op = new ModelNode();
            op.get(ModelDescriptionConstants.OP_ADDR).add(ModelDescriptionConstants.SUBSYSTEM, "logging");
            op.get(ModelDescriptionConstants.OP_ADDR).add("root-logger", "ROOT");
            op.get(ModelDescriptionConstants.OP).set("add-handler");
            op.get(ModelDescriptionConstants.NAME).set(HANDLER_NAME);
            result = client.getControllerClient().execute(op);
            assertSuccessResult(result);
        }

        private static void unassignAndRemoveCustomHandler(ManagementClient client) throws IOException {
            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP_ADDR).add(ModelDescriptionConstants.SUBSYSTEM, "logging");
            op.get(ModelDescriptionConstants.OP_ADDR).add("root-logger", "ROOT");
            op.get(ModelDescriptionConstants.OP).set("remove-handler");
            op.get(ModelDescriptionConstants.NAME).set(HANDLER_NAME);
            ModelNode result = client.getControllerClient().execute(op);
            assertSuccessResult(result);

            op = new ModelNode();
            op.get(ModelDescriptionConstants.OP_ADDR).add(ModelDescriptionConstants.SUBSYSTEM, "logging");
            op.get(ModelDescriptionConstants.OP_ADDR).add("file-handler", HANDLER_NAME);
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.REMOVE);
            result = client.getControllerClient().execute(op);
            assertSuccessResult(result);
        }

        @Override
        public void setup(ManagementClient client) throws Exception {
            // get the file for the custom logger
            logFile = getAbsoluteLogFilePath(client, FILE_NAME);

            // get a backup of max and min permissions
            maxPermissions = readPermissions(client, "maximum-permissions");
            minPermissions = readPermissions(client, "minimum-permissions");

            // create maximum and minimum set with an invalid permission
            ModelNode permissionProperties = new ModelNode().setEmptyObject();
            permissionProperties.get("class").set("java.util.PropertyPermission");
            permissionProperties.get("name").set("*");
            permissionProperties.get("actions").set("read");
            ModelNode permissionXnio = new ModelNode().setEmptyObject();
            permissionXnio.get("class").set("java.lang.RuntimePermission");
            permissionXnio.get("name").set("createXnioWorker");
            ModelNode permissionNet1= new ModelNode().setEmptyObject();
            permissionNet1.get("class").set("java.net.SocketPermission");
            permissionNet1.get("name").set(TestSuiteEnvironment.getServerAddress() + ":8080");
            permissionNet1.get("actions").set("listen,resolve");
            ModelNode permissionNet2= new ModelNode().setEmptyObject();
            permissionNet2.get("class").set("java.net.SocketPermission");
            permissionNet2.get("name").set("*");
            permissionNet2.get("actions").set("accept,resolve");
            ModelNode permissionBad = new ModelNode().setEmptyObject();
            permissionBad.get("class").set("java.error.InvalidPermission");
            writePermissions(client, "maximum-permissions", permissionProperties, permissionXnio, permissionNet1, permissionNet2, permissionBad);
            writePermissions(client, "minimum-permissions", permissionProperties, permissionXnio, permissionNet1, permissionNet2, permissionBad);

            // create the custom handler
            createAndAssignCustomHandler(client);

            // reload is needed
            ServerReload.executeReloadAndWaitForCompletion(client.getControllerClient());

            // deploy the application with the invalid permission
            final StringBuilder manifest = new StringBuilder();
            manifest.append("Dependencies: io.undertow.core");
            JavaArchive war = ShrinkWrap.create(JavaArchive.class, DEPLOY_NAME)
                    .addClasses(JavaVersionServiceActivator.class)
                    .addClasses(UndertowServiceActivator.DEPENDENCIES)
                    .addAsServiceProviderAndClasses(ServiceActivator.class, JavaVersionServiceActivator.class)
                    .addAsResource(new StringAsset(manifest.toString()), "META-INF/MANIFEST.MF")
                    .addAsManifestResource(new StringAsset("<permissions version=\"7\">"
                            + "    <permission>"
                            + "        <class-name>java.error.InvalidPermission</class-name>"
                            + "    </permission>"
                            + "</permissions>"), "permissions.xml");
            final ServerDeploymentHelper helper = new ServerDeploymentHelper(client.getControllerClient());
            helper.deploy(DEPLOY_NAME, war.as(ZipExporter.class).exportAsInputStream());
        }

        @Override
        public void tearDown(ManagementClient client) throws Exception {
            // undeploy
            final ServerDeploymentHelper helper = new ServerDeploymentHelper(client.getControllerClient());
            helper.undeploy(DEPLOY_NAME);

            // restore max and min permissions
            if (maxPermissions.isDefined()) {
                writePermissions(client, "maximum-permissions", maxPermissions.asList().toArray(new ModelNode[0]));
            } else {
                removePermissions(client, "maximum-permissions");
            }
            if (minPermissions.isDefined()) {
                writePermissions(client, "minimum-permissions", minPermissions.asList().toArray(new ModelNode[0]));
            } else {
                removePermissions(client, "minimum-permissions");
            }

            // remove the custom handler
            unassignAndRemoveCustomHandler(client);

            // reload is needed
            ServerReload.executeReloadAndWaitForCompletion(client.getControllerClient());

            Files.deleteIfExists(logFile);
        }
    }

    private static void assertSuccessResult(ModelNode result) {
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).toString());
        }
    }

    private static String resolveRelativePath(ManagementClient client, String relativePath) throws IOException {
        ModelNode address = PathAddress.pathAddress(
                PathElement.pathElement(ModelDescriptionConstants.PATH, relativePath)).toModelNode();
        ModelNode op = Operations.createReadAttributeOperation(address, ModelDescriptionConstants.PATH);
        ModelNode result = client.getControllerClient().execute(op);
        assertSuccessResult(result);
        return Operations.readResult(result).asString();
    }

    private static Path getAbsoluteLogFilePath(ManagementClient client, String filename) throws IOException {
        return Paths.get(resolveRelativePath(client, "jboss.server.log.dir"), filename);
    }

    private static void searchLog(final String msg) throws Exception {
        try (final BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            boolean logFound = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains(msg)) {
                    logFound = true;
                    break;
                }
            }
            Assert.assertTrue("Warning found in the log", logFound);
        }
    }

    @Test
    public void testDeployed() throws Exception {
        final URL url = TestSuiteEnvironment.getHttpUrl();
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpGet get = new HttpGet(url.toURI());
            HttpResponse response = client.execute(get);
            Assert.assertEquals("Response OK", HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals("Java version OK", System.getProperty("java.version"), EntityUtils.toString(response.getEntity()));
        }
        // check the warning messages exist on the log for the three types
        searchLog("The following permission could not be constructed and will be ignored in the maximum-set: (class=\"java.error.InvalidPermission\" name=\"\" actions=\"\")");
        searchLog("The following permission could not be constructed and will be ignored in the minimum-set: (class=\"java.error.InvalidPermission\" name=\"\" actions=\"\")");
        searchLog("The following permission could not be constructed and will be ignored in the deployment: (class=\"java.error.InvalidPermission\" name=\"\" actions=\"\")");
    }
}