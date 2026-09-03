/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EMPTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TARGET_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPLOAD_DEPLOYMENT_URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import jakarta.inject.Inject;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Integration tests for deployment source URL validation.
 * </p>
 * Note: SSRF protection disabled is not possible to test here since we need a file available from a link-local address to
 * perform the deployment. This case is only tested via Unit tests in
 * {@code org.wildfly.core.management.deployment.DeploymentSourceValidatorTestCase}.
 */
@ServerControl(manual = true)
@RunWith(WildFlyRunner.class)
public class ServerDeploymentSourceValidatorTestCase {

    @Inject
    private static ManagementClient managementClient;

    @Inject
    protected static ServerController container;

    private static File testWarFile;

    private boolean propsResolved;
    private String configDir;
    private String homeDir;
    private static String jbossArgs;
    private static final Duration CLEAN_OBSOLETE_CONTENT_INTERVAL = Duration.ofSeconds(5);

    @BeforeClass
    public static void createTestArchive() throws IOException {
        File archivesDir = new File("target", "test-archives");
        if (archivesDir.mkdirs() || archivesDir.exists()) {
            testWarFile = new File(archivesDir, "test-validation.war");
            if (!testWarFile.exists()) {
                // Create an empty WAR file for testing
                Manifest manifest = new Manifest();
                manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
                try (FileOutputStream fos = new FileOutputStream(testWarFile);
                        JarOutputStream jos = new JarOutputStream(fos, manifest)) {
                    // Empty WAR is sufficient for upload validation tests
                }
            }
        } else {
            fail("Failed to create test archives directory: " + archivesDir.getAbsolutePath());
        }

        // allow obsolete content to be cleaned up every X seconds, this will allow us to clean content uploaded to the
        // deployment repository that has not been deployed yet, for example the content uploaded by upload-deployment-url
        jbossArgs = System.getProperty("jboss.args");
        String serverArgs = jbossArgs == null ? "" : jbossArgs;
        serverArgs += " -Dorg.wildfly.unsupported.content.repository.obsolescence=" + CLEAN_OBSOLETE_CONTENT_INTERVAL.toMillis();
        System.setProperty("jboss.args", serverArgs);
        container.start();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        try {
            System.clearProperty("jboss.args");
            System.setProperty("jboss.args", jbossArgs == null ? "" : jbossArgs);
            cleanObsoleteContent(managementClient);
            TimeUnit.SECONDS.sleep(CLEAN_OBSOLETE_CONTENT_INTERVAL.toSeconds());
            cleanObsoleteContent(managementClient);
            Assert.assertTrue("Failed to recursively deleting " + testWarFile.getParentFile(),
                    deleteRecursive(testWarFile.getParentFile()));
        } finally {
            container.stop();
        }
    }

    @Before
    public void resolveServerDirectories() throws IOException {
        if (!propsResolved) {
            configDir = resolveServerExpression("${jboss.server.config.dir}");
            Assert.assertNotNull("jboss.server.config.dir must resolve", configDir);
            homeDir = resolveServerExpression("${jboss.home.dir}");
            Assert.assertNotNull("jboss.home.dir must resolve", homeDir);
            propsResolved = true;
        }
    }

    // --- upload-deployment-url ---

    @Test
    public void testUploadDeploymentUrl_fileUrlToConfigDir_rejected() throws Exception {
        String url = Paths.get(configDir, "standalone.xml").toUri().toURL().toString();
        ModelNode result = executeUploadDeploymentUrl(url);

        assertOperationFailed(result, "WFLYCTL0538",
                "upload-deployment-url with file:// to config dir should be rejected");
    }

    @Test
    public void testUploadDeploymentUrl_linkLocalAddress_rejected() throws Exception {
        String url = "http://169.254.169.254/latest/meta-data/iam/credentials";
        ModelNode result = executeUploadDeploymentUrl(url);

        assertOperationFailed(result, "WFLYCTL0539",
                "upload-deployment-url with link-local address should be rejected");
    }

    @Test
    public void testUploadDeploymentUrl_legitimateFileUrl_allowed() throws Exception {
        String url = testWarFile.toURI().toURL().toString();
        ModelNode result = executeUploadDeploymentUrl(url);

        Assert.assertTrue("upload-deployment-url with file:// to safe location should succeed, but got: "
                + result.toJSONString(false), Operations.isSuccessfulOutcome(result));
    }

    // --- full-replace-deployment ---

    @Test
    public void testFullReplaceDeployment_fileUrlToConfigDir_rejected() throws Exception {
        String deploymentName = "test-frp-url-blocked.war";
        try {
            deployTestWar(deploymentName);
            String url = Paths.get(configDir, "standalone.xml").toUri().toURL().toString();
            ModelNode result = executeFullReplaceDeploymentWithUrl(url, deploymentName);

            assertOperationFailed(result, "WFLYCTL0538",
                    "full-replace-deployment with file:// to config dir should be rejected");
        } finally {
            removeDeployment(deploymentName);
        }
    }

    @Test
    public void testFullReplaceDeployment_filePathToConfigDir_rejected() throws Exception {
        String deploymentName = "test-frp-path-blocked.war";
        try {
            deployTestWar(deploymentName);
            String path = Paths.get(configDir, "standalone.xml").toString();
            ModelNode result = executeFullReplaceDeploymentWithPath(path, deploymentName);

            assertOperationFailed(result, "WFLYCTL0538",
                    "full-replace-deployment with file:// to config dir should be rejected");
        } finally {
            removeDeployment(deploymentName);
        }
    }

    @Test
    public void testFullReplaceDeployment_linkLocalAddress_rejected() throws Exception {
        String deploymentName = "test-frp-linklocal.war";
        try {
            deployTestWar(deploymentName);
            String url = "http://169.254.169.254/latest/meta-data/iam/credentials";
            ModelNode result = executeFullReplaceDeploymentWithUrl(url, deploymentName);

            assertOperationFailed(result, "WFLYCTL0539",
                    "full-replace-deployment with link-local address should be rejected");
        } finally {
            removeDeployment(deploymentName);
        }
    }

    @Test
    public void testFullReplaceDeployment_legitimateFileUrl_allowed() throws Exception {
        String deploymentName = "test-frp-url-allowed.war";
        try {
            deployTestWar(deploymentName);
            String url = testWarFile.toURI().toURL().toString();
            ModelNode result = executeFullReplaceDeploymentWithUrl(url, deploymentName);

            Assert.assertTrue("full-replace-deployment with legitimate file:// URL should succeed, but got: "
                    + result.toJSONString(false), Operations.isSuccessfulOutcome(result));
        } finally {
            removeDeployment(deploymentName);
        }
    }

    @Test
    public void testFullReplaceDeployment_legitimatePath_allowed() throws Exception {
        String deploymentName = "test-frp-path-allowed.war";
        try {
            deployTestWar(deploymentName);
            String path = testWarFile.getAbsolutePath();
            ModelNode result = executeFullReplaceDeploymentWithPath(path, deploymentName);

            Assert.assertTrue("full-replace-deployment with legitimate path should succeed, but got: "
                    + result.toJSONString(false), Operations.isSuccessfulOutcome(result));
        } finally {
            removeDeployment(deploymentName);
        }
    }

    // --- /deployment=*:add ---

    @Test
    public void testDeploymentAdd_contentUrlToConfigDir_rejected() throws Exception {
        String url = Paths.get(configDir, "standalone.xml").toUri().toURL().toString();
        ModelNode result = executeDeploymentAddWithUrl(url, "rejected.war");

        assertOperationFailed(result, "WFLYCTL0538",
                "deployment add with content URL to config dir should be rejected");
    }

    @Test
    public void testDeploymentAdd_linkLocalAddress_rejected() throws Exception {
        String url = "http://169.254.169.254/latest/meta-data/iam/credentials";
        ModelNode result = executeDeploymentAddWithUrl(url, "rejected.war");

        assertOperationFailed(result, "WFLYCTL0539", "deployment add with link-local address should be rejected");
    }

    @Test
    public void testDeploymentAdd_legitimateFileUrl_allowed() throws Exception {
        String deploymentName = "test-add-url-allowed.war";
        try {
            String url = testWarFile.toURI().toURL().toString();
            ModelNode result = executeDeploymentAddWithUrl(url, deploymentName);

            Assert.assertTrue(
                    "deployment add with legitimate file:// URL should succeed, but got: " + result.toJSONString(false),
                    Operations.isSuccessfulOutcome(result));
        } finally {
            removeDeployment(deploymentName);
        }
    }

    @Test
    public void testDeploymentAdd_pathToConfigDir_rejected() throws Exception {
        String path = Paths.get(configDir, "standalone.xml").toString();
        ModelNode result = executeDeploymentAddWithPath(path, "rejected.war");

        assertOperationFailed(result, "WFLYCTL0538", "deployment add with path to config dir should be rejected");
    }

    @Test
    public void testDeploymentAdd_legitimatePath_allowed() throws Exception {
        String deploymentName = "test-add-path-allowed.war";
        try {
            String path = testWarFile.getAbsolutePath();
            ModelNode result = executeDeploymentAddWithPath(path, deploymentName);

            Assert.assertTrue(
                    "deployment add with legitimate path should succeed, but got: " + result.toJSONString(false),
                    Operations.isSuccessfulOutcome(result));
        } finally {
            removeDeployment(deploymentName);
        }
    }

    // --- /deployment=*:add-content ---

    @Test
    public void testDeploymentAddContent_urlToConfigDir_rejected() throws Exception {
        String deploymentName = "test-ac-url-blocked.war";
        try {
            createEmptyExplodedDeployment(deploymentName);
            String url = Paths.get(configDir, "standalone.xml").toUri().toURL().toString();
            ModelNode result = executeDeploymentAddContent(url, deploymentName, "WEB-INF/web.xml");

            assertOperationFailed(result, "WFLYCTL0538",
                    "deployment add-content with file:// to config dir should be rejected");
        } finally {
            removeDeployment(deploymentName);
        }
    }

    @Test
    public void testDeploymentAddContent_linkLocalAddress_rejected() throws Exception {
        String deploymentName = "test-ac-linklocal.war";
        try {
            createEmptyExplodedDeployment(deploymentName);
            String url = "http://169.254.169.254/latest/meta-data/iam/credentials";
            ModelNode result = executeDeploymentAddContent(url, deploymentName, "WEB-INF/web.xml");

            assertOperationFailed(result, "WFLYCTL0539",
                    "deployment add-content with link-local address should be rejected");
        } finally {
            removeDeployment(deploymentName);
        }
    }

    @Test
    public void testDeploymentAddContent_legitimateFileUrl_allowed() throws Exception {
        String deploymentName = "test-ac-url-allowed.war";
        try {
            createEmptyExplodedDeployment(deploymentName);
            String url = testWarFile.toURI().toURL().toString();
            ModelNode result = executeDeploymentAddContent(url, deploymentName, "WEB-INF/lib/test.jar");

            Assert.assertTrue("deployment add-content with legitimate file:// URL should succeed, but got: "
                    + result.toJSONString(false), Operations.isSuccessfulOutcome(result));
        } finally {
            removeDeployment(deploymentName);
        }
    }

    // --- /deployment-overlay=*/content=*:add ---

    @Test
    public void testOverlayContentAdd_urlToConfigDir_rejected() throws Exception {
        String overlayName = "test-overlay-url-blocked";
        try {
            createDeploymentOverlay(overlayName);
            String url = Paths.get(configDir, "standalone.xml").toUri().toURL().toString();
            ModelNode result = executeOverlayContentAdd(url, overlayName, "WEB-INF/web.xml");

            assertOperationFailed(result, "WFLYCTL0538",
                    "deployment-overlay content add with file:// to config dir should be rejected");
        } finally {
            removeDeploymentOverlay(overlayName);
        }
    }

    @Test
    public void testOverlayContentAdd_linkLocalAddress_rejected() throws Exception {
        String overlayName = "test-overlay-linklocal";
        try {
            createDeploymentOverlay(overlayName);
            String url = "http://169.254.169.254/latest/meta-data/iam/credentials";
            ModelNode result = executeOverlayContentAdd(url, overlayName, "WEB-INF/web.xml");

            assertOperationFailed(result, "WFLYCTL0539",
                    "deployment-overlay content add with link-local address should be rejected");
        } finally {
            removeDeploymentOverlay(overlayName);
        }
    }

    @Test
    public void testOverlayContentAdd_legitimateFileUrl_allowed() throws Exception {
        String overlayName = "test-overlay-url-allowed";
        try {
            createDeploymentOverlay(overlayName);
            String url = testWarFile.toURI().toURL().toString();
            ModelNode result = executeOverlayContentAdd(url, overlayName, "WEB-INF/web.xml");

            Assert.assertTrue("deployment-overlay content add with legitimate file:// URL should succeed, but got: "
                    + result.toJSONString(false), Operations.isSuccessfulOutcome(result));
        } finally {
            removeDeploymentOverlay(overlayName);
        }
    }

    // --- Scheme validation ---

    @Test
    public void testUploadDeploymentUrl_jarScheme_rejected() throws Exception {
        String url = "jar:file:///opt/lib/app.jar!/META-INF/MANIFEST.MF";
        ModelNode result = executeUploadDeploymentUrl(url);

        assertOperationFailed(result, "WFLYCTL0537", "upload-deployment-url with jar: scheme should be rejected");
    }

    // --- Cross-mode protection ---

    @Test
    public void testUploadDeploymentUrl_fileUrlToDomainDir_rejected() throws Exception {
        // On standalone, ${jboss.home.dir}/domain should be blocked
        String url = Paths.get(homeDir, "domain", "configuration", "domain.xml").toUri().toURL().toString();
        ModelNode result = executeUploadDeploymentUrl(url);

        assertOperationFailed(result, "WFLYCTL0538",
                "upload-deployment-url with file:// to domain dir should be rejected");
    }

    // --- User reg expression ---

    @Test
    public void testUploadDeploymentUrl_xml_allowed() throws Exception {
        addSystemProperty(managementClient, "jboss.deployment.source.allow", ".*\\.xml");
        try {
            String url = Paths.get(configDir, "standalone.xml").toUri().toURL().toString();
            ModelNode result = executeUploadDeploymentUrl(url);

            Assert.assertTrue(
                    "upload-deployment-url with extension .xml should succeed when "
                            + "jboss.deployment.source.allow=.*\\.xml, but got: " + result.toJSONString(false),
                    Operations.isSuccessfulOutcome(result));
        } finally {
            removeSystemProperty(managementClient, "jboss.deployment.source.allow");
        }
    }

    // --- Helper methods: execute operations ---

    private ModelNode executeUploadDeploymentUrl(String url) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(UPLOAD_DEPLOYMENT_URL);
        op.get(URL).set(url);
        return managementClient.getControllerClient().execute(op);
    }

    private ModelNode executeDeploymentAddWithUrl(String url, String deploymentName) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(NAME).set(deploymentName);
        op.get(RUNTIME_NAME).set(deploymentName);
        ModelNode contentNode = new ModelNode();
        contentNode.get(URL).set(url);
        op.get(CONTENT).setEmptyList();
        op.get(CONTENT).add(contentNode);
        return managementClient.getControllerClient().execute(op);
    }

    private ModelNode executeDeploymentAddWithPath(String path, String deploymentName) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(NAME).set(deploymentName);
        op.get(RUNTIME_NAME).set(deploymentName);
        ModelNode contentNode = new ModelNode();
        contentNode.get(PATH).set(path);
        contentNode.get(ARCHIVE).set(true);
        op.get(CONTENT).setEmptyList();
        op.get(CONTENT).add(contentNode);
        return managementClient.getControllerClient().execute(op);
    }

    private ModelNode executeFullReplaceDeploymentWithUrl(String url, String deploymentName) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(FULL_REPLACE_DEPLOYMENT);
        op.get(NAME).set(deploymentName);
        op.get(RUNTIME_NAME).set(deploymentName);
        ModelNode contentNode = new ModelNode();
        contentNode.get(URL).set(url);
        op.get(CONTENT).setEmptyList();
        op.get(CONTENT).add(contentNode);
        return managementClient.getControllerClient().execute(op);
    }

    private ModelNode executeFullReplaceDeploymentWithPath(String path, String deploymentName) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(FULL_REPLACE_DEPLOYMENT);
        op.get(NAME).set(deploymentName);
        op.get(RUNTIME_NAME).set(deploymentName);
        ModelNode contentNode = new ModelNode();
        contentNode.get(PATH).set(path);
        contentNode.get(ARCHIVE).set(true);
        op.get(CONTENT).setEmptyList();
        op.get(CONTENT).add(contentNode);
        return managementClient.getControllerClient().execute(op);
    }

    private ModelNode executeDeploymentAddContent(String url, String deploymentName, String targetPath)
            throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD_CONTENT);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        ModelNode contentNode = new ModelNode();
        contentNode.get(URL).set(url);
        contentNode.get(TARGET_PATH).set(targetPath);
        op.get(CONTENT).setEmptyList();
        op.get(CONTENT).add(contentNode);
        return managementClient.getControllerClient().execute(op);
    }

    private ModelNode executeOverlayContentAdd(String url, String overlayName, String contentPath) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(DEPLOYMENT_OVERLAY, overlayName).add(CONTENT, contentPath);
        ModelNode contentNode = new ModelNode();
        contentNode.get(URL).set(url);
        op.get(CONTENT).set(contentNode);
        return managementClient.getControllerClient().execute(op);
    }

    private static void cleanObsoleteContent(ManagementClient managementClient) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set("clean-obsolete-content");
        op.get(OP_ADDR).setEmptyList();
        ModelNode result = managementClient.getControllerClient().execute(op);
        Assert.assertTrue("clean-obsolete-content failed: " + result.toJSONString(false),
                Operations.isSuccessfulOutcome(result));
    }

    // --- Helper methods: setup and cleanup ---

    /**
     * Deploys the test WAR as a managed deployment. Used as a prerequisite for full-replace-deployment tests, which require the
     * deployment to already exist.
     */
    private void deployTestWar(String deploymentName) throws IOException {
        String url = testWarFile.toURI().toURL().toString();
        ModelNode result = executeDeploymentAddWithUrl(url, deploymentName);
        Assert.assertTrue("Setup failed: could not deploy " + deploymentName + ": " + result.toJSONString(false),
                Operations.isSuccessfulOutcome(result));
    }

    /**
     * Creates an empty exploded managed deployment. Used as a prerequisite for add-content tests, which require a managed
     * exploded deployment.
     */
    private void createEmptyExplodedDeployment(String deploymentName) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(NAME).set(deploymentName);
        op.get(RUNTIME_NAME).set(deploymentName);
        ModelNode contentNode = new ModelNode();
        contentNode.get(EMPTY).set(true);
        op.get(CONTENT).setEmptyList();
        op.get(CONTENT).add(contentNode);
        ModelNode result = managementClient.getControllerClient().execute(op);
        Assert.assertTrue("Setup failed: could not create exploded deployment " + deploymentName + ": "
                + result.toJSONString(false), Operations.isSuccessfulOutcome(result));
    }

    /**
     * Creates a deployment overlay resource. Used as a prerequisite for deployment-overlay content add tests.
     */
    private void createDeploymentOverlay(String overlayName) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(DEPLOYMENT_OVERLAY, overlayName);
        ModelNode result = managementClient.getControllerClient().execute(op);
        Assert.assertTrue(
                "Setup failed: could not create deployment overlay " + overlayName + ": " + result.toJSONString(false),
                Operations.isSuccessfulOutcome(result));
    }

    /**
     * Removes a deployment (best-effort cleanup, silently ignores failures).
     */
    private void removeDeployment(String deploymentName) throws IOException {
        ModelNode op = Operations.createRemoveOperation(new ModelNode().add(DEPLOYMENT, deploymentName));
        managementClient.getControllerClient().execute(op);
    }

    /**
     * Adds a server system property via the management API. Fails the test if the operation is not successful.
     */
    private static void addSystemProperty(ManagementClient managementClient, String name, String value)
            throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(SYSTEM_PROPERTY, name);
        op.get(VALUE).set(value);
        ModelNode result = managementClient.getControllerClient().execute(op);
        Assert.assertTrue("Failed to add system property " + name + ": " + result.toJSONString(false),
                Operations.isSuccessfulOutcome(result));
    }

    /**
     * Removes a server system property (best-effort cleanup, silently ignores failures).
     */
    private static void removeSystemProperty(ManagementClient managementClient, String name) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set("remove");
        op.get(OP_ADDR).add(SYSTEM_PROPERTY, name);
        managementClient.getControllerClient().execute(op);
    }

    /**
     * Removes a deployment overlay and all its children (best-effort cleanup).
     */
    private void removeDeploymentOverlay(String overlayName) throws IOException {
        ModelNode op = Operations.createRemoveOperation(new ModelNode().add(DEPLOYMENT_OVERLAY, overlayName));
        managementClient.getControllerClient().execute(op);
    }

    // --- Helper methods: assertion and resolution ---

    /**
     * Resolves a server-side expression (e.g., {@code ${jboss.server.config.dir}}) via the {@code resolve-expression}
     * management operation.
     */
    private String resolveServerExpression(String expression) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set("resolve-expression");
        op.get("expression").set(expression);
        ModelNode result = managementClient.getControllerClient().execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            return result.get(RESULT).asString();
        }
        return null;
    }

    private static void assertOperationFailed(ModelNode result, String expectedErrorCode, String message) {
        Assert.assertFalse(message, Operations.isSuccessfulOutcome(result));
        String failureDescription = result.hasDefined("failure-description")
                ? result.get("failure-description").asString()
                : "";
        Assert.assertTrue(message + " -- expected error code " + expectedErrorCode + " but got: " + failureDescription,
                failureDescription.contains(expectedErrorCode));
    }

    // --- Helper ---

    private static boolean deleteRecursive(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            return file.delete();
        }
        return true;
    }
}
