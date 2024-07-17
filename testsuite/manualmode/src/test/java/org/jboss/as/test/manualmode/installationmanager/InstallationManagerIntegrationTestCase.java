/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.installationmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import jakarta.inject.Inject;

import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.cli.UpdateCommand;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.Repository;
import org.wildfly.test.installationmanager.TestInstallationManager;
import org.wildfly.test.installationmanager.TestInstallationManagerFactory;

/**
 * Tests the high-level Installation Manager commands in standalone mode environment. It uses a mocked implementation of the
 * installation manager, which provides dummy data for the test.
 * <p>
 * The purpose of this test is to ensure that the high-level commands, which rely on low-level management operations, can
 * retrieve the data from the mocked implementation.
 * <p>
 * See InstMgrResourceTestCase for low-level management operation unit testing.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InstallationManagerIntegrationTestCase extends AbstractCliTestBase {

    private static final String MODULE_NAME = "org.jboss.prospero";
    private static TestModule testModule;

    private static Path prepareServerDir;

    static final Path TARGET_DIR = Paths.get(System.getProperty("basedir", ".")).resolve("target");
    static Path customPatchBaseDir;

    @Inject
    protected static ManagementClient client;
    @Inject
    protected static ServerController container;

    @BeforeClass
    public static void setupDomain() throws Exception {
        createTestModule();
        container.start();
        AbstractCliTestBase.initCLI();

        prepareServerDir = Paths.get(TestSuiteEnvironment.getJBossHome()).resolve("standalone").resolve("tmp")
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        customPatchBaseDir = Paths.get(TestSuiteEnvironment.getJBossHome()).resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            AbstractCliTestBase.closeCLI();
        } finally {
            container.stop();
            testModule.remove();
        }
    }

    private static void createTestModule() throws IOException {
        testModule = new TestModule(MODULE_NAME, "org.wildfly.installation-manager.api");
        testModule.addResource("test-mock-installation-manager.jar").addClass(TestInstallationManager.class).addClass(TestInstallationManagerFactory.class)
                .addAsManifestResource("META-INF/services/org.wildfly.installationmanager.spi.InstallationManagerFactory",
                        "services/org.wildfly.installationmanager.spi.InstallationManagerFactory");
        testModule.create(true);
    }

    @After
    public void clean() throws IOException {
        // Clean any previous state
        assertTrue(cli.sendLine("installer clean", false));
        Assert.assertTrue(!Files.exists(prepareServerDir));

       for(File testZip : TARGET_DIR.toFile().listFiles((dir, name) -> name.startsWith("installation-manager") && name.endsWith(".zip"))) {
           Files.deleteIfExists(testZip.toPath());
        }
    }

    @Test
    public void rejectsHost() {
        final List<String> commands = List.of("update", "clean", "revert", "history", "channel-list",
                "channel-add --channel-name test --manifest test --repositories test", "channel-edit --channel-name test --manifest test --repositories test",
                "channel-remove --channel-name test", "upload-custom-patch --custom-patch-file=dummy --manifest=manifest");

        for (String command : commands) {
            AssertionError exception = assertThrows(AssertionError.class, () -> {
                cli.sendLine("installer " + command + " --host=test");
            });

            String expectedMessage = "The --host option is not available in the current context.";
            String actualMessage = exception.getMessage();

            assertTrue(actualMessage, actualMessage.contains(expectedMessage));
        }
    }

    public static String buildChannelOutput(Channel channel) {
        final String returnChar = Util.isWindows() ? "\r\n" : "\n";

        StringBuilder sb = new StringBuilder("-------").append(returnChar).append("# " + channel.getName()).append(returnChar);

        if (channel.getManifestUrl().isPresent()) {
            sb.append("  manifest: " + channel.getManifestUrl().get()).append(returnChar);
        } else if (channel.getManifestCoordinate().isPresent()) {
            sb.append("  manifest: " + channel.getManifestCoordinate().get()).append(returnChar);
        }

        sb.append("  repositories:").append(returnChar);

        for (Repository repository : channel.getRepositories()) {
            sb.append("    id: " + repository.getId()).append(returnChar).append("    url: " + repository.getUrl()).append(returnChar);
        }

        return sb.toString();
    }

    @Test
    public void _a_listChannel() throws Exception {
        cli.sendLine("installer channel-list");
        String output = cli.readOutput();

        TestInstallationManager.initialize();

        StringBuilder expected = new StringBuilder();
        for (Channel channel : TestInstallationManager.lstChannels) {
            expected.append(buildChannelOutput(channel));
        }
        expected.append("-------");

        Assert.assertEquals(expected.toString(), output);
    }

    @Test
    public void _b_addChannel() throws IOException {
        String channelName = "test-primary";
        String manifestGavOrUrl = "group:artifact:primary-1.0.0.Final";
        String repoStr = "https://primary.com";

        cli.sendLine(
                "installer channel-add --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr);
        String output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' created.", output);

        cli.sendLine("installer channel-list");
        output = cli.readOutput();

        StringBuilder expected = new StringBuilder();

        TestInstallationManager.initialize();
        for (Channel channel : TestInstallationManager.lstChannels) {
            expected.append(buildChannelOutput(channel));
        }

        Repository repository = new Repository("id0", repoStr);
        Channel newChannel = new Channel(channelName, List.of(repository), manifestGavOrUrl);
        expected.append(buildChannelOutput(newChannel));
        expected.append("-------");

        Assert.assertEquals(expected.toString(), output);
    }

    @Test
    public void _c_removeChannel() {
        String channelName = "channel-test-1";
        cli.sendLine("installer channel-remove --channel-name=" + channelName);
        String output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' has been successfully removed.", output);
    }

    @Test
    public void _d_editChannel() {
        String channelName = "channel-test-2";
        String manifestGavOrUrl = "group:artifact:edited-1.0.0.Final";
        String repoStr = "https://edited.com";

        cli.sendLine(
                "installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr);
        String output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' has been modified.", output);

        cli.sendLine("installer channel-list");
        output = cli.readOutput();

        Repository repository = new Repository("id0", repoStr);
        Channel newChannel = new Channel(channelName, List.of(repository), manifestGavOrUrl);
        String expected = buildChannelOutput(newChannel);
        Assert.assertTrue(output, output.contains(expected));
    }

    @Test
    public void _e_editChannelNotExist() {
        String channelName = "unknown";
        String manifestGavOrUrl = "group:artifact:edited-1.0.0.Final";
        String repoStr = "https://edited.com";

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr);
        });

        String expectedMessage = "Channel '" + channelName + "' is not present.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void _f_testHistory() throws IOException {
        cli.sendLine("installer history");
        String output = cli.readOutput();

        String[] lines = output.split("\n");
        assertEquals(4, lines.length);

        TestInstallationManager.initialize();
        Set<String> expectedValues = TestInstallationManager.history.keySet();
        Set<String> actual = new HashSet<>(Arrays.asList(lines));
        for (String actualStr : actual) {
            for (Iterator<String> it = expectedValues.iterator(); it.hasNext();) {
                if (actualStr.contains(it.next())) {
                    it.remove();
                }
            }
        }
        Assert.assertTrue(Arrays.asList(lines).toString(), expectedValues.isEmpty());
    }

    @Test
    public void testRevisionDetails() throws Exception {
        cli.sendLine("installer history --revision=dummy");
        String output = cli.readOutput();

        assertTrue(output, output.contains("org.test.groupid1:org.test.artifact1.installed"));
        assertTrue(output, output.contains("org.test.groupid1:org.test.artifact1.removed"));
        assertTrue(output, output.contains("org.test.groupid1:org.test.artifact1.updated"));
        assertTrue(output, output.contains("[Added channel] channel-test-0"));
        assertTrue(output, output.contains("[Removed channel] channel-test-0"));
        assertTrue(output, output.contains("[Updated channel] channel-test-0"));
    }

    @Test
    public void testCreateSnapShot() throws IOException {
        Path exportPath = TARGET_DIR.normalize().toAbsolutePath().resolve("generated.zip");
        try {
            cli.sendLine("attachment save --operation=/core-service=installer:clone-export() --file=" + exportPath);
            String output = cli.readOutput();
            String expected = "File saved to " + exportPath;
            assertEquals(expected, output);

            boolean found = false;
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(exportPath.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.startsWith("installation-manager-test-") && entryName.endsWith(".tmp")) {
                        found = true;
                        break;
                    }
                }
            }
            Assert.assertTrue("Cannot found the expected entry added by the TestInstallationManager.java in the clone export Zip result", found);
        } finally {
            File expectedExportFile = exportPath.toFile();
            if (expectedExportFile.exists()) {
                expectedExportFile.delete();
            }
        }
    }

    @Test
    public void updateWithDryRun() {
        cli.sendLine("installer update --dry-run");
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
    }

    @Test
    public void updateWithConfirm() throws IOException {
        cli.sendLine("installer update --repositories=id0::http://localhost --confirm");
        String output = cli.readOutput();

        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithConfirmRepositories() throws IOException {
        cli.sendLine("installer update --confirm --repositories=id0::http://localhost");
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithConfirmLocalCache() throws IOException {
        cli.sendLine("installer update --confirm --local-cache=" + TARGET_DIR);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateLocalCacheWithUseDefaultLocalCache() {
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --local-cache=" + TARGET_DIR + " --use-default-local-cache");
        });

        String expectedMessage = "WFLYIM0021:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));

        exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --local-cache=" + TARGET_DIR + " --use-default-local-cache");
        });

        expectedMessage = "WFLYIM0021:";
        actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void cannotUseConfirmAndDryRunAtTheSameTime() {
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --confirm --dry-run");
        });

        String expectedMessage = UpdateCommand.CONFIRM_OPTION + " and " + UpdateCommand.DRY_RUN_OPTION + " cannot be used at the same time.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void updateWithConfirmUsingMavenZipFile() throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        cli.sendLine("installer update --confirm --maven-repo-files=" + target);
        String output = cli.readOutput();
        verifyUpdatePrepareDirWasCreated(output);

    }
    @Test
    public void updateWithConfirmUsingMultipleMavenZipFiles() throws IOException {
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-two").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        cli.sendLine("installer update --confirm --maven-repo-files=" + targetOne + "," + targetTwo);
        String output = cli.readOutput();
        verifyUpdatePrepareDirWasCreated(output);
    }

    public void verifyUpdatePrepareDirWasCreated(String output) throws IOException {
        final Path expectedPreparedServerDir = prepareServerDir;

        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertTrue(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertTrue(Files.exists(expectedPreparedServerDir) && Files.isDirectory(expectedPreparedServerDir));
        Assert.assertTrue(expectedPreparedServerDir + " does not contain the expected file marker",
                directoryOnlyContains(expectedPreparedServerDir, p -> expectedPreparedServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }


    @Test
    public void updateWithDryRunMavenZipFile() throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        cli.sendLine("installer update --dry-run --maven-repo-files=" + target);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertFalse(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertFalse(Files.exists(prepareServerDir));
    }

    @Test
    public void updateWithDryRunMultipleMavenZipFile() throws IOException {
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        cli.sendLine("installer update --dry-run --maven-repo-files=" + targetOne +","+ targetTwo);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertFalse(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertFalse(Files.exists(prepareServerDir));
    }

    @Test
    public void updateUsingBlockingTimeout() throws IOException {
        assertTrue(cli.sendLine("installer update --confirm --headers={blocking-timeout=100}", false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --repositories=id0::http://localhost --maven-repo-files=" + target);
        });

        String expectedMessage = "WFLYIM0012:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertWithMavenZipFile() throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-files=" + target, false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithMultipleMavenZipFiles() throws IOException {
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-two").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-files=" + targetOne + "," + targetTwo, false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithNoResolveLocalMavenCache() throws IOException {
        assertTrue(cli.sendLine("installer revert --revision=dummy --no-resolve-local-cache", false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                Files.list(prepareServerDir).allMatch(p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithUseDefaultLocalCache() throws IOException {
        assertTrue(cli.sendLine("installer revert --revision=dummy --use-default-local-cache", false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithLocalCacheMavenCache() throws IOException {
        assertTrue(cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR, false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertLocalCacheWithUseDefaultLocalCache() {
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " --use-default-local-cache");
        });

        String expectedMessage = "WFLYIM0021:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));

        exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " --use-default-local-cache=true");
        });

        expectedMessage = "WFLYIM0021:";
        actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertWithOffline() throws IOException {
        assertTrue(cli.sendLine("installer revert --revision=dummy --offline", false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithHeaders() throws IOException {
        assertTrue(cli.sendLine("installer revert --revision=dummy --headers={blocking-timeout=100}", false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithRepositories() throws IOException {
        assertTrue(cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost", false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void simpleRevert() throws IOException {
        assertTrue(cli.sendLine("installer revert --revision=dummy", false));

        Assert.assertTrue(Files.exists(prepareServerDir) && Files.isDirectory(prepareServerDir));
        Assert.assertTrue(prepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(prepareServerDir, p -> prepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertCannotBeUsedWithoutRevision() {
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert");
        });

        String expectedMessage = "WFLYCTL0155:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost --maven-repo-files=" + target);
        });

        String expectedMessage = "WFLYIM0012:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void uploadAndRemoveCustomPatch() throws IOException {
        String patchManifestGA = "group:artifact";
        Path customPatchDir = customPatchBaseDir.resolve(patchManifestGA.replace(":", "_"));

        createAndUploadCustomPatch(patchManifestGA, customPatchDir, "test-repo-one", "artifact-one");
        removeCustomPatch(patchManifestGA, customPatchDir);
    }

    @Test
    public void removeNonExistingCustomPatch() {
        String patchManifestGA = "group-unknown:artifact-unknown";
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer remove-custom-patch --manifest=" + patchManifestGA);
        });
        String expectedMessage = "WFLYIM0020:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage, actualMessage.contains(expectedMessage));

    }

    @Test
    public void uploadAndRemoveMultipleCustomPatches() throws IOException {
        String patchManifestGA_1 = "group1:artifact1";
        Path customPatchDir_1 = customPatchBaseDir.resolve(patchManifestGA_1.replace(":", "_"));
        createAndUploadCustomPatch(patchManifestGA_1, customPatchDir_1, "test-repo-one", "artifact-one");

        String patchManifestGA_2 = "group2:artifact2";
        Path customPatchDir_2 = customPatchBaseDir.resolve(patchManifestGA_2.replace(":", "_"));
        createAndUploadCustomPatch(patchManifestGA_2, customPatchDir_2, "test-repo-two", "artifact-two");

        removeCustomPatch(patchManifestGA_1, customPatchDir_1);

        // check we still have the second patch
        final Path customPatchMavenRepo = customPatchDir_2.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(Files.exists(customPatchMavenRepo) && Files.isDirectory(customPatchMavenRepo));
        Assert.assertTrue(customPatchDir_2 + " does not contain the expected maven repository",
                directoryOnlyContains(customPatchMavenRepo, p -> customPatchMavenRepo.relativize(p).toString().equals("artifact-two")));

        // remove the patch 2
        removeCustomPatch(patchManifestGA_2, customPatchDir_2);
    }

    @Test
    public void testProductInfoIncludesInstallerInfo() throws Exception {
        cli.sendLine(":product-info");

        ModelNode response = cli.readAllAsOpResult().getResponseNode();

        final List<ModelNode> results = response.get("result").asList();
        for (ModelNode res : results) {
            final ModelNode summary = res.get("result").get("summary");
            if (!summary.hasDefined("instance-identifier")) {
                continue;
            }

            Assert.assertTrue(summary.toString(), summary.hasDefined("last-update-date"));
            Assert.assertTrue(summary.hasDefined("channel-versions"));
            Assert.assertEquals(List.of(new ModelNode("Update 1")), summary.get("channel-versions").asList());
        }
    }

    public void createAndUploadCustomPatch(String customPatchManifest, Path customPatchDir, String mavenDirToZip, String expectedArtifact) throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource(mavenDirToZip).getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        // verify the patch doesn't exist yet
        final Path customPatchMavenRepo = customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertFalse(Files.exists(customPatchMavenRepo));

        Assert.assertTrue(
                cli.sendLine("installer upload-custom-patch --custom-patch-file=" + target + " --manifest=" + customPatchManifest, false));
        // verify clean operation without arguments don't remove the patch
        Assert.assertTrue(cli.sendLine("installer clean", false));
        Assert.assertTrue(Files.exists(customPatchMavenRepo) && Files.isDirectory(customPatchMavenRepo));
        Assert.assertTrue(customPatchDir + " does not contain the expected artifact included in the custom patch Zip file",
                directoryOnlyContains(customPatchMavenRepo, p -> customPatchMavenRepo.relativize(p).toString().equals(expectedArtifact)));
    }

    public void removeCustomPatch(String customPatchManifest, Path customPatchDir) {
        // remove the custom patch
        final Path customPatchMavenRepo = customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(cli.sendLine("installer remove-custom-patch --manifest=" + customPatchManifest, false));
        Assert.assertFalse(Files.exists(customPatchMavenRepo));
    }

    @Test
    public void cannotShutDownIfNoServerPrepared() {
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("shutdown --perform-installation");
        });
        String expectedMessage = "WFLYSRV0295:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    public static void zipDir(Path sourcePath, Path target) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(target.toFile()); ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                    if (!sourcePath.equals(dir)) {
                        zos.putNextEntry(new ZipEntry(sourcePath.relativize(dir) + "/"));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    zos.putNextEntry(new ZipEntry(sourcePath.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public String getCauseLogFailure(String description, String expectedLogCode) {
        return "Unexpected Error Code. Got " + description + " It was expected: " + expectedLogCode;
    }

    public boolean directoryOnlyContains(Path directory, Predicate<Path> match) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.allMatch(match);
        }
    }
}
