/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.installationmanager;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.module.util.TestModule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.cli.UpdateCommand;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.Repository;
import org.wildfly.test.installationmanager.TestInstallationManager;
import org.wildfly.test.installationmanager.TestInstallationManagerFactory;

/**
 * Tests the high-level Installation Manager commands in domain mode environment. It uses a mocked implementation of the
 * installation manager, which provides dummy data for the test.
 * <p>
 * The purpose of this test is to ensure that the high-level commands, which rely on low-level management operations, can
 * retrieve the data from the mocked implementation.
 * <p>
 * See InstMgrResourceTestCase for low-level management operation unit testing.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InstallationManagerIntegrationTestCase extends AbstractCliTestBase {

    private static final String MODULE_NAME = "org.jboss.prospero";
    private static DomainTestSupport testSupport;
    private static TestModule testModule;

    private static Path primaryPrepareServerDir;
    private static Path secondaryPrepareServerDir;

    static final Path TARGET_DIR = Paths.get(System.getProperty("basedir", ".")).resolve("target");
    static Path primaryCustomPatchBaseDir;
    static Path secondaryCustomPatchBaseDir;

    @BeforeClass
    public static void setupDomain() throws Exception {
        createTestModule();
        testSupport = DomainTestSuite.createSupport(InstallationManagerIntegrationTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.primaryAddress);

        primaryPrepareServerDir = Paths.get(testSupport.getDomainPrimaryConfiguration().getDomainDirectory()).resolve("tmp")
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        secondaryPrepareServerDir = Paths.get(testSupport.getDomainSecondaryConfiguration().getDomainDirectory()).resolve("tmp")
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        primaryCustomPatchBaseDir = Paths.get(testSupport.getDomainPrimaryConfiguration().getJbossHome()).resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);

        secondaryCustomPatchBaseDir = Paths.get(testSupport.getDomainSecondaryConfiguration().getJbossHome()).resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            AbstractCliTestBase.closeCLI();
            DomainTestSuite.stopSupport();
            testSupport = null;
        } finally {
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
        String host = "secondary";
        // Clean any previous state
        assertTrue(cli.sendLine("installer clean --host=" + host, false));
        Assert.assertTrue(!Files.exists(secondaryPrepareServerDir));

        host = "primary";
        // Clean any previous state
        assertTrue(cli.sendLine("installer clean --host=" + host, false));
        Assert.assertTrue(!Files.exists(primaryPrepareServerDir));

       for(File testZip : TARGET_DIR.toFile().listFiles((dir, name) -> name.startsWith("installation-manager") && name.endsWith(".zip"))) {
           Files.deleteIfExists(testZip.toPath());
        }
    }

    @Test
    public void requireHost() {
        final List<String> commands = List.of("update", "clean", "revert", "history", "channel-list",
                "channel-add --channel-name test --manifest test --repositories test", "channel-edit --channel-name test --manifest test --repositories test",
                "channel-remove --channel-name test", "upload-custom-patch --custom-patch-file=dummy --manifest=manifest");

        for (String command : commands) {
            AssertionError exception = assertThrows(AssertionError.class, () -> {
                cli.sendLine("installer " + command);
            });

            String expectedMessage = "The --host option must be used in domain mode.";
            String actualMessage = exception.getMessage();

            assertTrue(actualMessage, actualMessage.contains(expectedMessage));
        }
    }

    public static String buildChannelOutput(Channel channel) {
        final String returnChar = Util.isWindows() ? "\r\n" : "\n";

        StringBuilder sb = new StringBuilder("-------").append(returnChar).append("#" + channel.getName()).append(returnChar);

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
    public void _a_listChannel() throws IOException {
        cli.sendLine("installer channel-list --host=primary");
        String output = cli.readOutput();

        TestInstallationManager.initialize();

        StringBuilder expected = new StringBuilder();
        for (Channel channel : TestInstallationManager.lstChannels) {
            expected.append(buildChannelOutput(channel));
        }
        expected.append("-------");

        Assert.assertEquals(expected.toString(), output);

        cli.sendLine("installer channel-list --host=secondary");
        output = cli.readOutput();

        Assert.assertEquals(expected.toString(), output);
    }

    @Test
    public void _b_addChannel() throws IOException {
        String channelName = "test-primary";
        String manifestGavOrUrl = "group:artifact:primary-1.0.0.Final";
        String repoStr = "https://primary.com";
        String host = "primary";

        cli.sendLine(
                "installer channel-add --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
        String output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' created.", output);

        cli.sendLine("installer channel-list --host=" + host);
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

        // verify the secondary Host
        expected = new StringBuilder();
        channelName = "test-secondary";
        manifestGavOrUrl = "/test";
        repoStr = "https://secondary.com";
        host = "secondary";

        cli.sendLine(
                "installer channel-add --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
        output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' created.", output);

        cli.sendLine("installer channel-list --host=" + host);
        output = cli.readOutput();

        TestInstallationManager.initialize();
        for (Channel channel : TestInstallationManager.lstChannels) {
            expected.append(buildChannelOutput(channel));
        }

        repository = new Repository("id0", repoStr);
        newChannel = new Channel(channelName, List.of(repository), Paths.get(manifestGavOrUrl).toUri().toURL());
        expected.append(buildChannelOutput(newChannel));
        expected.append("-------");

        Assert.assertEquals(expected.toString(), output);
    }

    @Test
    public void _c_removeChannel() {
        String channelName = "channel-test-1";
        String host = "primary";
        cli.sendLine("installer channel-remove --channel-name=" + channelName + " --host=" + host);
        String output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' has been successfully removed.", output);

        channelName = "channel-test-2";
        host = "secondary";
        cli.sendLine("installer channel-remove --channel-name=" + channelName + " --host=" + host);
        output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' has been successfully removed.", output);
    }

    @Test
    public void _d_editChannel() {
        String channelName = "channel-test-2";
        String manifestGavOrUrl = "group:artifact:edited-1.0.0.Final";
        String repoStr = "https://edited.com";
        String host = "primary";

        cli.sendLine(
                "installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
        String output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' has been modified.", output);

        cli.sendLine("installer channel-list --host=" + host);
        output = cli.readOutput();

        Repository repository = new Repository("id0", repoStr);
        Channel newChannel = new Channel(channelName, List.of(repository), manifestGavOrUrl);
        String expected = buildChannelOutput(newChannel);
        Assert.assertTrue(output, output.contains(expected));

        channelName = "channel-test-1";
        manifestGavOrUrl = "group:artifact:edited-1.0.0.Final";
        repoStr = "https://edited.com";
        host = "secondary";

        cli.sendLine(
                "installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
        output = cli.readOutput();
        Assert.assertEquals("Channel '" + channelName + "' has been modified.", output);

        cli.sendLine("installer channel-list --host=" + host);
        output = cli.readOutput();

        repository = new Repository("id0", repoStr);
        newChannel = new Channel(channelName, List.of(repository), manifestGavOrUrl);
        expected = buildChannelOutput(newChannel);
        Assert.assertTrue(output, output.contains(expected));
    }

    @Test
    public void _e_editChannelNotExist() {
        String channelName = "unknown";
        String manifestGavOrUrl = "group:artifact:edited-1.0.0.Final";
        String repoStr = "https://edited.com";
        String host = "primary";

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host="
                    + host);
        });

        String expectedMessage = "Channel '" + channelName + "' is not present.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void _f_testHistory() throws IOException {
        String host = "secondary";
        cli.sendLine("installer history --host=" + host);
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
        String host = "primary";
        cli.sendLine("installer history --revision=dummy --host=" + host);
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
        String host = "primary";
        Path exportPath = TARGET_DIR.normalize().toAbsolutePath().resolve("generated.zip");
        try {
            cli.sendLine("attachment save --operation=/host=" + host + "/core-service=installer:clone-export() --file=" + exportPath);
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
        String host = "primary";
        cli.sendLine("installer update --dry-run --host=" + host);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
    }

    @Test
    public void updateWithConfirm() throws IOException {
        String host = "primary";
        cli.sendLine("installer update --repositories=id0::http://localhost --confirm --host=" + host);
        String output = cli.readOutput();

        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithConfirmRepositories() throws IOException {
        String host = "primary";
        cli.sendLine("installer update --confirm --repositories=id0::http://localhost --host=" + host);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithConfirmLocalCache() throws IOException {
        String host = "primary";

        cli.sendLine("installer update --confirm --local-cache=" + TARGET_DIR + " --host=" + host);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void cannotUseConfirmAndDryRunAtTheSameTime() {
        String host = "primary";
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --confirm --dry-run --host=" + host);
        });

        String expectedMessage = UpdateCommand.CONFIRM_OPTION + " and " + UpdateCommand.DRY_RUN_OPTION + " cannot be used at the same time.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void updateWithConfirmUsingMavenZipFile() throws IOException {
        String host = "primary";
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        cli.sendLine("installer update --confirm --maven-repo-files=" + target + " --host=" + host);
        String output = cli.readOutput();
        verifyUpdatePrepareDirWasCreated(host, output);

    }
    @Test
    public void updateWithConfirmUsingMultipleMavenZipFiles() throws IOException {
        String host = "primary";
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-two").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        cli.sendLine("installer update --confirm --maven-repo-files=" + targetOne + "," + targetTwo + " --host=" + host);
        String output = cli.readOutput();
        verifyUpdatePrepareDirWasCreated(host, output);
    }

    public void verifyUpdatePrepareDirWasCreated(String host, String output) throws IOException {
        final Path expectedPreparedServerDir = host.equals("primary") ? primaryPrepareServerDir : secondaryPrepareServerDir;

        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertTrue(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertTrue(Files.exists(expectedPreparedServerDir) && Files.isDirectory(expectedPreparedServerDir));
        Assert.assertTrue(expectedPreparedServerDir + " does not contain the expected file marker",
                Files.list(expectedPreparedServerDir).allMatch(p -> expectedPreparedServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }


    @Test
    public void updateWithDryRunMavenZipFile() throws IOException {
        String host = "primary";
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        cli.sendLine("installer update --dry-run --maven-repo-files=" + target + " --host=" + host);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertFalse(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertFalse(Files.exists(primaryPrepareServerDir));
    }

    @Test
    public void updateWithDryRunMultipleMavenZipFile() throws IOException {
        String host = "primary";
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        cli.sendLine("installer update --dry-run --maven-repo-files=" + targetOne +","+ targetTwo + " --host=" + host);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertFalse(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertFalse(Files.exists(primaryPrepareServerDir));
    }

    @Test
    public void updateUsingBlockingTimeout() throws IOException {
        String host = "secondary";
        assertTrue(cli.sendLine("installer update --confirm --headers={blocking-timeout=100} --host=" + host, false));

        Assert.assertTrue(Files.exists(secondaryPrepareServerDir) && Files.isDirectory(secondaryPrepareServerDir));
        Assert.assertTrue(secondaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(secondaryPrepareServerDir).allMatch(p -> secondaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --repositories=id0::http://localhost --maven-repo-files=" + target + " --host=" + host);
        });

        String expectedMessage = "WFLYIM0012:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertWithMavenZipFile() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-files=" + target + " --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithMultipleMavenZipFiles() throws IOException {
        String host = "primary";

        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-two").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-files=" + targetOne + "," + targetTwo + " --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithNoResolveLocalMavenCache() throws IOException {
        String host = "primary";

        assertTrue(cli.sendLine("installer revert --revision=dummy --no-resolve-local-cache --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithLocalCacheMavenCache() throws IOException {
        String host = "primary";

        assertTrue(cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithOffline() throws IOException {
        String host = "primary";

        assertTrue(cli.sendLine("installer revert --revision=dummy --offline --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithHeaders() throws IOException {
        String host = "secondary";

        assertTrue(cli.sendLine("installer revert --revision=dummy --headers={blocking-timeout=100} --host=" + host, false));

        Assert.assertTrue(Files.exists(secondaryPrepareServerDir) && Files.isDirectory(secondaryPrepareServerDir));
        Assert.assertTrue(secondaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(secondaryPrepareServerDir).allMatch(p -> secondaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithRepositories() throws IOException {
        String host = "primary";

        assertTrue(cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void simpleRevert() throws IOException {
        String host = "primary";

        assertTrue(cli.sendLine("installer revert --revision=dummy --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertCannotBeUsedWithoutRevision() {
        String host = "primary";

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --host=" + host);
        });

        String expectedMessage = "WFLYCTL0155:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost --maven-repo-files=" + target + " --host=" + host);
        });

        String expectedMessage = "WFLYIM0012:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void uploadAndRemoveCustomPatch() throws IOException {
        String host = "primary";
        String patchManifestGA = "group:artifact";
        Path hostCustomPatchDir = primaryCustomPatchBaseDir.resolve(patchManifestGA.replace(":", "_"));

        createAndUploadCustomPatch(patchManifestGA, host, hostCustomPatchDir, "test-repo-one", "artifact-one");
        removeCustomPatch(patchManifestGA, host, hostCustomPatchDir);
    }

    @Test
    public void uploadAndRemoveMultipleCustomPatches() throws IOException {
        String host = "primary";
        String patchManifestGA_1 = "group1:artifact1";
        Path hostCustomPatchDir_1 = primaryCustomPatchBaseDir.resolve(patchManifestGA_1.replace(":", "_"));
        createAndUploadCustomPatch(patchManifestGA_1, host, hostCustomPatchDir_1, "test-repo-one", "artifact-one");

        String patchManifestGA_2 = "group2:artifact2";
        Path hostCustomPatchDir_2 = primaryCustomPatchBaseDir.resolve(patchManifestGA_2.replace(":", "_"));
        createAndUploadCustomPatch(patchManifestGA_2, host, hostCustomPatchDir_2, "test-repo-two", "artifact-two");

        removeCustomPatch(patchManifestGA_1, host, hostCustomPatchDir_1);

        // check we still have the second patch
        final Path customPatchMavenRepo = hostCustomPatchDir_2.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(Files.exists(customPatchMavenRepo) && Files.isDirectory(customPatchMavenRepo));
        Assert.assertTrue(hostCustomPatchDir_2 + " does not contain the expected maven repository",
                Files.list(customPatchMavenRepo).allMatch(p -> customPatchMavenRepo.relativize(p).toString().equals("artifact-two")));

        // remove the patch 2
        removeCustomPatch(patchManifestGA_2, host, hostCustomPatchDir_2);
    }

    public void createAndUploadCustomPatch(String customPatchManifest, String host, Path hostCustomPatchDir, String mavenDirToZip, String expectedArtifact) throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource(mavenDirToZip).getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        // verify the patch doesn't exist yet
        final Path customPatchMavenRepo = hostCustomPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(!Files.exists(customPatchMavenRepo));

        Assert.assertTrue(
                cli.sendLine("installer upload-custom-patch --custom-patch-file=" + target + " --manifest=" + customPatchManifest + " --host=" + host, false));
        // verify clean operation without arguments don't remove the patch
        Assert.assertTrue(cli.sendLine("installer clean --host=" + host, false));
        Assert.assertTrue(Files.exists(customPatchMavenRepo) && Files.isDirectory(customPatchMavenRepo));
        Assert.assertTrue(hostCustomPatchDir + " does not contain the expected artifact included in the custom patch Zip file",
                Files.list(customPatchMavenRepo).allMatch(p -> customPatchMavenRepo.relativize(p).toString().equals(expectedArtifact)));
    }

    public void removeCustomPatch(String customPatchManifest, String host, Path hostCustomPatchDir) {
        // remove the custom patch
        final Path primaryCustomPatchMavenRepo = hostCustomPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(cli.sendLine("installer clean --custom-patch-manifest=" + customPatchManifest + " --host=" + host, false));
        Assert.assertFalse(Files.exists(primaryCustomPatchMavenRepo));
    }

    @Test
    public void cannotShutDownIfNoServerPrepared() {
        String host = "primary";
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("shutdown --perform-installation --host=" + host);
        });
        String expectedMessage = "WFLYHC0218:";
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
}
