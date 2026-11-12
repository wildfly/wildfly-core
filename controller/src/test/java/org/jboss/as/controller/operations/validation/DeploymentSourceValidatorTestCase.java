/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.as.controller.OperationFailedException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests for {@link DeploymentSourceValidator}.
 */
public class DeploymentSourceValidatorTestCase {

    private static Path tempServerHome;
    private static Path configDir;
    private static Path dataDir;
    private static Path logDir;
    private static Path tmpDir;
    private static Path contentDir;
    private static Path domainDir;
    private static Path symlinkToConfig;
    private static String validDeploymentWarURL;
    private static DeploymentSourceValidator standaloneValidator;

    @BeforeClass
    public static void setUp() throws IOException {
        tempServerHome = Files.createTempDirectory("wfcore-test-home");
        configDir = Files.createDirectories(tempServerHome.resolve("standalone/configuration"));
        dataDir = Files.createDirectories(tempServerHome.resolve("standalone/data"));
        logDir = Files.createDirectories(tempServerHome.resolve("standalone/log"));
        tmpDir = Files.createDirectories(tempServerHome.resolve("standalone/tmp"));
        contentDir = Files.createDirectories(tempServerHome.resolve("standalone/data/content"));
        domainDir = Files.createDirectories(tempServerHome.resolve("domain"));

        Files.writeString(configDir.resolve("standalone.xml"), "<server/>");
        Files.writeString(configDir.resolve("mgmt-users.properties"), "admin=hash");

        Path validDeploymentWar = tempServerHome.resolve("my-test-deployment.war");
        Files.writeString(validDeploymentWar, "<web-app/>");
        validDeploymentWarURL = validDeploymentWar.toUri().toURL().toString();

        symlinkToConfig = tempServerHome.resolve("config-link");
        try {
            Files.createSymbolicLink(symlinkToConfig, configDir);
        } catch (IOException | UnsupportedOperationException e) {
            symlinkToConfig = null;
        }

        System.setProperty("jboss.home.dir", tempServerHome.toString());
        System.setProperty("jboss.server.base.dir", tempServerHome.resolve("standalone").toString());
        System.setProperty("jboss.server.config.dir", configDir.toString());
        System.setProperty("jboss.server.data.dir", dataDir.toString());
        System.setProperty("jboss.server.log.dir", logDir.toString());
        System.setProperty("jboss.server.temp.dir", tmpDir.toString());
        System.setProperty("jboss.server.content.dir", contentDir.toString());

        System.clearProperty(DeploymentSourceValidator.PROP_ALLOW);
        System.clearProperty(DeploymentSourceValidator.PROP_ALLOW_LINK_LOCAL_ADDRESS);

        standaloneValidator = DeploymentSourceValidator.forStandalone();
    }

    @AfterClass
    public static void tearDown() {
        // Clear all system properties set during setup
        System.clearProperty("jboss.home.dir");
        System.clearProperty("jboss.server.base.dir");
        System.clearProperty("jboss.server.config.dir");
        System.clearProperty("jboss.server.data.dir");
        System.clearProperty("jboss.server.log.dir");
        System.clearProperty("jboss.server.temp.dir");
        System.clearProperty("jboss.server.content.dir");
        System.clearProperty(DeploymentSourceValidator.PROP_ALLOW);
        System.clearProperty(DeploymentSourceValidator.PROP_ALLOW_LINK_LOCAL_ADDRESS);
    }

    // --- Scheme validation ---

    @Test
    public void testHttpSchemeAllowed() throws OperationFailedException {
        standaloneValidator.validateUrl("http://127.0.0.1/app.war");
    }

    @Test
    public void testHttpsSchemeAllowed() throws OperationFailedException {
        standaloneValidator.validateUrl("https://127.0.0.1/app.war");
    }

    @Test
    public void testFtpSchemeAllowed() throws OperationFailedException {
        standaloneValidator.validateUrl("ftp://username:password@hostname:21/path/to/app.war");
    }

    @Test
    public void testFileSchemeAllowed() throws OperationFailedException {
        standaloneValidator.validateUrl(validDeploymentWarURL);
    }

    @Test
    public void testJarSchemeRejected() {
        Assert.assertThrows(OperationFailedException.class,
                () -> standaloneValidator.validateUrl("jar:file:///opt/lib/app.jar!/META-INF/"));
    }

    // --- Blocked-path validation ---

    @Test
    public void testFileUrlToConfigDirRejected() throws Exception {
        String url = configDir.resolve("standalone.xml").toUri().toURL().toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validateUrl(url));
    }

    @Test
    public void testFileUrlToDataDirRejected() throws Exception {
        String url = dataDir.resolve("something").resolve("sensitive.properties").toUri().toURL().toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validateUrl(url));
    }

    @Test
    public void testFileUrlToLogDirRejected() throws Exception {
        String url = logDir.resolve("server.log").toUri().toURL().toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validateUrl(url));
    }

    @Test
    public void testFileUrlToTmpDirRejected() throws Exception {
        String url = tmpDir.resolve("subdirectory").resolve("temp.dat").toUri().toURL().toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validateUrl(url));
    }

    @Test
    public void testFileUrlToContentDirRejected() throws Exception {
        String url = contentDir.resolve("abc123").toUri().toURL().toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validateUrl(url));
    }

    @Test
    public void testFileUrlOutsideBlockedDirsAllowed() throws OperationFailedException {
        standaloneValidator.validateUrl(validDeploymentWarURL);
    }

    // --- Symlink bypass prevention ---

    @Test
    public void testSymlinkBypassRejected() throws Exception {
        Assume.assumeTrue("Symlinks not supported on this platform", symlinkToConfig != null);
        String url = symlinkToConfig.resolve("mgmt-users.properties").toUri().toURL().toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validateUrl(url));
    }

    // --- SSRF validation ---

    @Test
    public void testSSRFLinkLocalRejected() {
        Assert.assertThrows(OperationFailedException.class,
                () -> standaloneValidator.validateUrl("http://169.254.169.254/latest/meta-data/iam/credentials"));
    }

    @Test
    public void testSSRFLooopbackAddressAllowed() throws OperationFailedException {
        standaloneValidator.validateUrl("http://127.0.0.1/deployments/app.war");
    }

    // --- File URL path valid ---

    @Test
    public void testFileUrlValidation_outsideBlockedDirs() throws OperationFailedException {
        standaloneValidator.validateUrl(validDeploymentWarURL);
    }

    // --- Filesystem path validation (unmanaged deployments) ---

    @Test
    public void testValidatePath_outsideBlockedDirs() throws OperationFailedException {
        standaloneValidator.validatePath("my-test-deployment.war", tempServerHome.toString());
    }

    @Test
    public void testValidatePath_insideConfigDir() {
        String path = configDir.resolve("standalone.xml").toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validatePath(path, null));
    }

    @Test
    public void testValidatePath_insideDataDir() {
        String path = "sensitive.properties";
        Assert.assertThrows(OperationFailedException.class,
                () -> standaloneValidator.validatePath(path, dataDir.toString()));
    }

    @Test
    public void testValidatePath_symlinkBypass() {
        Assume.assumeTrue("Symlinks not supported on this platform", symlinkToConfig != null);
        String path = symlinkToConfig.resolve("mgmt-users.properties").toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validatePath(path, null));
    }

    @Test
    public void testValidatePath_symlinkBypass_withUserProperty() throws OperationFailedException {
        Assume.assumeTrue("Symlinks not supported on this platform", symlinkToConfig != null);
        String path = symlinkToConfig.resolve("mgmt-users.properties").toString();
        try {
            System.setProperty(DeploymentSourceValidator.PROP_ALLOW, ".*\\.properties");
            standaloneValidator.validatePath(path, null);
        } finally {
            System.clearProperty(DeploymentSourceValidator.PROP_ALLOW);
        }
    }

    // --- Cross-mode protection ---

    @Test
    public void testCrossModeProtection_domainDirBlocked() throws Exception {
        String url = domainDir.resolve("configuration").resolve("domain.xml").toUri().toURL().toString();
        Assert.assertThrows(OperationFailedException.class, () -> standaloneValidator.validateUrl(url));
    }

    // --- Domain validator via forDomain() with system properties ---

    @Test
    public void testDomainValidator_blocksDomainConfigDir() throws IOException {
        Path domainConfigDir = Files.createDirectories(tempServerHome.resolve("domain/configuration"));
        try {
            System.setProperty("jboss.domain.base.dir", tempServerHome.resolve("domain").toString());
            System.setProperty("jboss.domain.config.dir", domainConfigDir.toString());

            // Use the constructor directly: forDomain() returns a shared cached instance whose blocked paths
            // were computed at first access, before these test-only properties were set.
            DeploymentSourceValidator domainValidator = new DeploymentSourceValidator(
                    new String[] { "jboss.domain.config.dir", "jboss.domain.data.dir", "jboss.domain.log.dir",
                            "jboss.domain.temp.dir", "jboss.domain.servers.dir", "jboss.domain.content.dir" },
                    "standalone");

            String domainConfigUrl = domainConfigDir.resolve("host.xml").toUri().toURL().toString();
            Assert.assertThrows(OperationFailedException.class, () -> domainValidator.validateUrl(domainConfigUrl));

            String standaloneConfigUrl = configDir.resolve("standalone.xml").toUri().toURL().toString();
            Assert.assertThrows(OperationFailedException.class, () -> domainValidator.validateUrl(standaloneConfigUrl));
        } finally {
            System.clearProperty("jboss.domain.base.dir");
            System.clearProperty("jboss.domain.config.dir");
        }
    }

    // --- Regex override mode ---

    @Test
    public void testRegexOverrideMode_matchingUrlAllowed() throws OperationFailedException {
        try {
            System.setProperty(DeploymentSourceValidator.PROP_ALLOW, ".*\\.war");
            standaloneValidator.validateUrl("http://127.0.0.1/app.war");
        } finally {
            System.clearProperty(DeploymentSourceValidator.PROP_ALLOW);
        }
    }

    @Test
    public void testRegexOverrideMode_nonMatchingUrlRejected() {
        try {
            System.setProperty(DeploymentSourceValidator.PROP_ALLOW, ".*\\.war");
            Assert.assertThrows(OperationFailedException.class,
                    () -> standaloneValidator.validateUrl("http://127.0.0.1/app.jar"));
        } finally {
            System.clearProperty(DeploymentSourceValidator.PROP_ALLOW);
        }
    }

    @Test
    public void testRegexOverrideMode_schemeAndPathChecksBypassed() throws Exception {
        try {
            System.setProperty(DeploymentSourceValidator.PROP_ALLOW, ".*");
            System.setProperty(DeploymentSourceValidator.PROP_ALLOW_LINK_LOCAL_ADDRESS, "true");

            standaloneValidator.validateUrl("jar:file:///opt/lib/app.jar!/META-INF/");
            standaloneValidator.validateUrl(configDir.resolve("standalone.xml").toUri().toURL().toString());
            standaloneValidator.validateUrl("http://169.254.169.254/latest/meta-data/iam/credentials");
        } finally {
            System.clearProperty(DeploymentSourceValidator.PROP_ALLOW);
            System.clearProperty(DeploymentSourceValidator.PROP_ALLOW_LINK_LOCAL_ADDRESS);
        }
    }

    // --- Link-local toggle ---

    @Test
    public void testLinkLocalAllowedWhenPropertyTrue() throws OperationFailedException {
        try {
            System.setProperty(DeploymentSourceValidator.PROP_ALLOW_LINK_LOCAL_ADDRESS, "true");
            standaloneValidator.validateUrl("http://169.254.169.254/latest/meta-data/iam/credentials");
        } finally {
            System.clearProperty(DeploymentSourceValidator.PROP_ALLOW_LINK_LOCAL_ADDRESS);
        }
    }

    @Test
    public void testLinkLocalNotAllowedWhenPropertyFalse() {
        try {
            System.setProperty(DeploymentSourceValidator.PROP_ALLOW_LINK_LOCAL_ADDRESS, "false");
            Assert.assertThrows(OperationFailedException.class,
                    () -> standaloneValidator.validateUrl("http://169.254.169.254/latest/meta-data/iam/credentials"));
        } finally {
            System.clearProperty(DeploymentSourceValidator.PROP_ALLOW_LINK_LOCAL_ADDRESS);
        }
    }

    @Test
    public void testLinkLocalNotAllowed_withRegexOverride_SSRFStillApplies() {
        try {
            System.setProperty(DeploymentSourceValidator.PROP_ALLOW, ".*");
            Assert.assertThrows(OperationFailedException.class,
                    () -> standaloneValidator.validateUrl("http://169.254.169.254/latest/meta-data/iam/credentials"));
        } finally {
            System.clearProperty(DeploymentSourceValidator.PROP_ALLOW);
        }
    }
}
