/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TARGET_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPLOAD_DEPLOYMENT_URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration tests for deployment source URL and path validation in domain mode.
 * </p>
 * Note: SSRF protection disabled is not possible to test here since we need a file available from a link-local address to
 * perform the deployment. This case is only tested via Unit tests in
 * {@code org.wildfly.core.management.deployment.DeploymentSourceValidatorTestCase}.
 */
public class DomainDeploymentSourceValidatorTestCase {
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil primaryLifecycleUtil;
    private static DomainClient domainClient;
    private static final PathAddress HOST_PRIMARY = PathAddress.pathAddress("host", "primary");
    private static File testWarFile;
    private boolean propsResolved;
    private String configDir;
    private String homeDir;

    @BeforeClass
    public static void setupDomain() throws Exception {
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
        testSupport = DomainTestSupport
                .createAndStartDefaultSupport(DomainDeploymentSourceValidatorTestCase.class.getName());
        primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainClient = primaryLifecycleUtil.getDomainClient();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            assertThat("testSupport", testSupport, is(notNullValue()));
            Assert.assertTrue("Failed to recursively deleting " + testWarFile.getParentFile(),
                    deleteRecursive(testWarFile.getParentFile()));
            testSupport.close();
        } finally {
            primaryLifecycleUtil = null;
            testSupport = null;
        }
    }

    @Before
    public void resolveServerDirectories() throws IOException {
        if (!propsResolved) {
            configDir = resolveServerExpression(domainClient, HOST_PRIMARY, "${jboss.domain.config.dir}");
            Assert.assertNotNull("jboss.domain.config.dir must resolve", configDir);
            homeDir = resolveServerExpression(domainClient, HOST_PRIMARY, "${jboss.home.dir}");
            Assert.assertNotNull("jboss.home.dir must resolve", homeDir);
            propsResolved = true;
        }
    }

    // --- upload-deployment-url ---

    @Test
    public void testUploadDeploymentUrl_fileUrlToConfigDir_rejected() throws Exception {
        String url = Paths.get(configDir, "testing-domain-standard.xml").toUri().toURL().toString();
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
            String url = Paths.get(configDir, "testing-domain-standard.xml").toUri().toURL().toString();
            ModelNode result = executeFullReplaceDeploymentWithUrl(url, deploymentName);

            assertOperationFailed(result, "WFLYCTL0538",
                    "full-replace-deployment with file:// to config dir should be rejected");
        } finally {
            removeDeployment(deploymentName);
        }
    }

    @Test
    public void testFullReplaceDeployment_filePathToConfigDir_rejected() throws Exception {
        String deploymentName = "test-frp-path-rejected.war";
        try {
            deployTestWar(deploymentName);
            String path = Paths.get(configDir, "testing-domain-standard.xml").toString();
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
        String url = Paths.get(configDir, "testing-domain-standard.xml").toUri().toURL().toString();
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
            // already tested by other tests that requires a war deployed but ... it does not hurt to test it again
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
        String path = Paths.get(configDir, "testing-domain-standard.xml").toString();
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
    // Note: explode deployments do not allow content to be added from a filesystem path, only from a URL

    @Test
    public void testDeploymentAddContent_urlToConfigDir_rejected() throws Exception {
        String deploymentName = "test-ac-url-blocked.war";
        try {
            createEmptyExplodedDeployment(deploymentName);
            String url = Paths.get(configDir, "testing-domain-standard.xml").toUri().toURL().toString();
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
    // Note: deployment overlay content does not allow content to be added from a filesystem path, only from a URL

    @Test
    public void testOverlayContentAdd_urlToConfigDir_rejected() throws Exception {
        String overlayName = "test-overlay-url-blocked";
        try {
            createDeploymentOverlay(overlayName);
            String url = Paths.get(configDir, "testing-domain-standard.xml").toUri().toURL().toString();
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
            String url = "http://169.254.169.254/latest/web.xml";
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
            ModelNode result = executeOverlayContentAdd(url, overlayName, "/");

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
    public void testUploadDeploymentUrl_fileUrlToStandaloneDir_rejected() throws Exception {
        String url = Paths.get(homeDir, "standalone", "configuration", "standalone.xml").toUri().toURL().toString();
        ModelNode result = executeUploadDeploymentUrl(url);

        assertOperationFailed(result, "WFLYCTL0538",
                "upload-deployment-url with file:// to standalone dir should be rejected");
    }

    // --- User regex override ---

    @Test
    public void testUploadDeploymentUrl_userRegexAllowsBlockedPath() throws Exception {
        WildFlyManagedConfiguration config = primaryLifecycleUtil.getConfiguration();
        String originalHostCommandLineProperties = config.getHostCommandLineProperties();

        primaryLifecycleUtil.stop();
        config.addHostCommandLineProperty("-Djboss.deployment.source.allow=.*\\.xml");

        primaryLifecycleUtil.start();
        domainClient = primaryLifecycleUtil.getDomainClient();

        String deploymentName = "test-regex-allowed.war";
        try {
            String url = Paths.get(configDir, "testing-domain-standard.xml").toUri().toURL().toString();
            ModelNode result = executeDeploymentAddWithUrl(url, deploymentName);

            Assert.assertTrue(
                    "deployment add from config dir should succeed when jboss.deployment.source.allow=.*\\.xml, but got: "
                            + result.toJSONString(false),
                    Operations.isSuccessfulOutcome(result));
        } finally {
            removeDeployment(deploymentName);

            primaryLifecycleUtil.stop();
            config.setHostCommandLineProperties(originalHostCommandLineProperties);
            primaryLifecycleUtil.start();
            domainClient = primaryLifecycleUtil.getDomainClient();
        }
    }

    // --- Helper methods: execute operations ---

    private ModelNode executeUploadDeploymentUrl(String url) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(UPLOAD_DEPLOYMENT_URL);
        op.get(URL).set(url);
        return domainClient.execute(op);
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
        return domainClient.execute(op);
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
        return domainClient.execute(op);
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
        return domainClient.execute(op);
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
        return domainClient.execute(op);
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
        return domainClient.execute(op);
    }

    private ModelNode executeOverlayContentAdd(String url, String overlayName, String contentPath) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(DEPLOYMENT_OVERLAY, overlayName).add(CONTENT, contentPath);
        ModelNode contentNode = new ModelNode();
        contentNode.get(URL).set(url);
        op.get(CONTENT).set(contentNode);
        return domainClient.execute(op);
    }

    // --- Helper methods: setup and cleanup ---

    /**
     * Deploys the test WAR as a managed deployment at the domain level. Used as a prerequisite for full-replace-deployment
     * tests, which require the deployment to already exist.
     */
    private void deployTestWar(String deploymentName) throws IOException {
        String url = testWarFile.toURI().toURL().toString();
        ModelNode result = executeDeploymentAddWithUrl(url, deploymentName);
        Assert.assertTrue("Setup failed: could not deploy " + deploymentName + ": " + result.toJSONString(false),
                Operations.isSuccessfulOutcome(result));
    }

    /**
     * Creates an empty exploded managed deployment at the domain level. Used as a prerequisite for add-content tests, which
     * require a managed exploded deployment.
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
        ModelNode result = domainClient.execute(op);
        Assert.assertTrue("Setup failed: could not create exploded deployment " + deploymentName + ": "
                + result.toJSONString(false), Operations.isSuccessfulOutcome(result));
    }

    /**
     * Creates a deployment overlay resource at the domain level. Used as a prerequisite for deployment-overlay content add
     * tests.
     */
    private void createDeploymentOverlay(String overlayName) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(DEPLOYMENT_OVERLAY, overlayName);
        ModelNode result = domainClient.execute(op);
        Assert.assertTrue(
                "Setup failed: could not create deployment overlay " + overlayName + ": " + result.toJSONString(false),
                Operations.isSuccessfulOutcome(result));
    }

    /**
     * Removes a domain-level deployment (best-effort cleanup, silently ignores failures).
     */
    private void removeDeployment(String deploymentName) throws IOException {
        ModelNode op = Operations.createRemoveOperation(new ModelNode().add(DEPLOYMENT, deploymentName));
        domainClient.execute(op);
    }

    /**
     * Removes a deployment overlay and all its children (best-effort cleanup).
     */
    private void removeDeploymentOverlay(String overlayName) throws IOException {
        ModelNode op = Operations.createRemoveOperation(new ModelNode().add(DEPLOYMENT_OVERLAY, overlayName));
        domainClient.execute(op);
    }

    // --- Helper methods: assertion and resolution ---

    /**
     * Resolves a server-side expression (e.g., {@code ${jboss.domain.config.dir}}) via the {@code resolve-expression}
     * management operation.
     */
    private static String resolveServerExpression(DomainClient domainClient, PathAddress address, String expression)
            throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set("resolve-expression");
        op.get(OP_ADDR).set(address.toModelNode());
        op.get("expression").set(expression);
        ModelNode result = domainClient.execute(op);
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
        return false;
    }
}
