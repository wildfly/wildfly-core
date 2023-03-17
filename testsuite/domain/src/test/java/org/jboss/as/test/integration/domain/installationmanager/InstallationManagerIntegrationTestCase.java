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
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
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
import java.util.zip.ZipOutputStream;

import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.module.util.TestModule;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
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
 * The purpose of this test is to ensure that the high-level commands, which rely on low-level management operations,
 * can retrieve the data from the mocked implementation.
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
    static Path primaryCustomPatchDir;
    static Path secondaryCustomPatchDir;


    @BeforeClass
    public static void setupDomain() throws Exception {
        createTestModule();
        testSupport = DomainTestSuite.createSupport(InstallationManagerIntegrationTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.primaryAddress);

        primaryPrepareServerDir = Paths.get(testSupport.getDomainPrimaryConfiguration().getDomainDirectory())
                .resolve("tmp")
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        secondaryPrepareServerDir = Paths.get(testSupport.getDomainSecondaryConfiguration().getDomainDirectory())
                .resolve("tmp")
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        primaryCustomPatchDir = Paths.get(testSupport.getDomainPrimaryConfiguration().getJbossHome())
                .resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);

        secondaryCustomPatchDir = Paths.get(testSupport.getDomainSecondaryConfiguration().getJbossHome())
                .resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);
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
        testModule.addResource("test-mock-installation-manager.jar")
                .addClass(TestInstallationManager.class)
                .addClass(TestInstallationManagerFactory.class)
                .addAsManifestResource("META-INF/services/org.wildfly.installationmanager.spi.InstallationManagerFactory",
                        "services/org.wildfly.installationmanager.spi.InstallationManagerFactory");
        testModule.create(true);
    }

    @Before
    public void clean() {
        String host = "secondary";
        // Clean any previous state
        assertTrue(cli.sendLine("installer clean --host=" + host, false));
        Assert.assertTrue(!Files.exists(secondaryPrepareServerDir));

        host = "primary";
        // Clean any previous state
        assertTrue(cli.sendLine("installer clean --host=" + host, false));
        Assert.assertTrue(!Files.exists(primaryPrepareServerDir));
    }

    @Test
    public void requireHost() {
        final List<String> commands = List.of("update", "clean", "revert", "history", "clone-export --path test",
                "channel-list", "channel-add --channel-name test --manifest test --repositories test",
                "channel-edit --channel-name test --manifest test --repositories test",
                "channel-remove --channel-name test",
                "upload-custom-patch --custom-patch-file=dummy --manifest=manifest");

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

        StringBuilder sb = new StringBuilder("-------").append(returnChar)
                .append("#" + channel.getName()).append(returnChar);

        if (channel.getManifestUrl().isPresent()) {
            sb.append("  manifest: " + channel.getManifestUrl().get()).append(returnChar);
        } else if (channel.getManifestCoordinate().isPresent()) {
            sb.append("  manifest: " + channel.getManifestCoordinate().get()).append(returnChar);
        }

        sb.append("  repositories:").append(returnChar);

        for (Repository repository : channel.getRepositories()) {
            sb.append("    id: " + repository.getId()).append(returnChar)
                    .append("    url: " + repository.getUrl()).append(returnChar);
        }

        return sb.toString();
    }

    @Test
    public void _a_listChannel() {
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
    public void _b_addChannel() throws MalformedURLException {
        String channelName = "test-primary";
        String manifestGavOrUrl = "group:artifact:primary-1.0.0.Final";
        String repoStr = "https://primary.com";
        String host = "primary";

        cli.sendLine("installer channel-add --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
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

        cli.sendLine("installer channel-add --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
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

        cli.sendLine("installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
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

        cli.sendLine("installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
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
            cli.sendLine("installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + " --host=" + host);
        });

        String expectedMessage = "Channel '" + channelName + "' is not present.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void _f_testHistory() {
        String host = "secondary";
        cli.sendLine("installer history --host=" + host);
        String output = cli.readOutput();

        String[] lines = output.split("\n");
        assertEquals(4, lines.length);

        TestInstallationManager.initialize();
        Set<String> expectedValues = TestInstallationManager.history.keySet();
        Set<String> actual = new HashSet<>(Arrays.asList(lines));
        for (String actualStr : actual) {
            for (Iterator<String> it = expectedValues.iterator(); it.hasNext(); ) {
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
    public void testCreateSnapShot() {
        String exportPath = Paths.get(testSupport.getDomainPrimaryConfiguration().getJbossHome()).toString();
        String host = "primary";
        cli.sendLine("installer clone-export --path=" + exportPath + " --host=" + host);
        String output = cli.readOutput();
        String expected = "Installation metadata was exported to [" + Paths.get(exportPath).resolve("generated.zip") + "]";
        assertEquals(expected, output);

        exportPath = Paths.get(testSupport.getDomainPrimaryConfiguration().getJbossHome()).resolve("test-export.zip").toString();
        host = "secondary";
        cli.sendLine("installer clone-export --path=" + exportPath + " --host=" + host);
        output = cli.readOutput();
        expected = "Installation metadata was exported to [" + exportPath + "]";
        assertEquals(expected, output);
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
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
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
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
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
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
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
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);


        cli.sendLine("installer update --confirm --maven-repo-file=" + target + " --host=" + host);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertTrue(output, output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithDryRunMavenZipFile() throws IOException {
        String host = "primary";
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);


        cli.sendLine("installer update --dry-run --maven-repo-file=" + target + " --host=" + host);
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertFalse(output, output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertFalse(Files.exists(primaryPrepareServerDir));
    }


    @Test
    public void updateUsingBlockingTimeout() throws IOException {
        String host = "secondary";
        assertTrue(cli.sendLine("installer update --confirm --headers={blocking-timeout=100} --host=" + host, false));

        Assert.assertTrue(Files.exists(secondaryPrepareServerDir) && Files.isDirectory(secondaryPrepareServerDir));
        Assert.assertTrue(secondaryPrepareServerDir + " does not contain the expected file marker", Files.list(secondaryPrepareServerDir).allMatch(p -> secondaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);


        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --repositories=id0::http://localhost --maven-repo-file=" + target + " --host=" + host);
        });

        String expectedMessage = "WFLYIM0015";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage.contains(expectedMessage));
    }


    @Test
    public void revertWithRepositories() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-file=" + target + " --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithNoResolveLocalMavenCache() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        assertTrue(cli.sendLine("installer revert --revision=dummy --no-resolve-local-cache --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithLocalCacheMavenCache() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        assertTrue(cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithOffline() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        assertTrue(cli.sendLine("installer revert --revision=dummy --offline --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithHeaders() throws IOException {
        String host = "secondary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        assertTrue(cli.sendLine("installer revert --revision=dummy --headers={blocking-timeout=100} --host=" + host, false));

        Assert.assertTrue(Files.exists(secondaryPrepareServerDir) && Files.isDirectory(secondaryPrepareServerDir));
        Assert.assertTrue(secondaryPrepareServerDir + " does not contain the expected file marker", Files.list(secondaryPrepareServerDir).allMatch(p -> secondaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithMavenZipFile() throws IOException {
        String host = "primary";

        assertTrue(cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }


    @Test
    public void revert() throws IOException {
        String host = "primary";

        assertTrue(cli.sendLine("installer revert --revision=dummy --host=" + host, false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker", Files.list(primaryPrepareServerDir).allMatch(p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertCannotBeUsedWithoutRevision() {
        String host = "primary";

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --host=" + host);
        });

        String expectedMessage = "WFLYCTL0155";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        String host = "primary";

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);


        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost --maven-repo-file=" + target + " --host=" + host);
        });

        String expectedMessage = "WFLYIM0015";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    public void uploadAndRemoveCustomPatch() throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        String host = "primary";
        Assert.assertTrue(cli.sendLine("installer upload-custom-patch --custom-patch-file=" + target + " --manifest=group:artifact --host=" + host, false));
        // verify clean operation without arguments don't remove the patch
        Assert.assertTrue(cli.sendLine("installer clean --host=" + host, false));

        final Path primaryCustomPatchMavenRepo = primaryCustomPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(Files.exists(primaryCustomPatchDir) && Files.isDirectory(primaryCustomPatchDir));
        Assert.assertTrue(primaryCustomPatchDir + " does not contain the expected maven repository",
                Files.list(primaryCustomPatchMavenRepo).allMatch(p -> primaryCustomPatchMavenRepo.relativize(p).toString().equals("artifact")));

        // remove the custom patch
        Assert.assertTrue(cli.sendLine("installer clean --custom-patch --host=" + host, false));
        Assert.assertFalse(Files.exists(primaryCustomPatchMavenRepo));



        host = "secondary";
        Assert.assertTrue(cli.sendLine("installer upload-custom-patch --custom-patch-file=" + target + " --manifest=/tmp --host=" + host, false));
        // verify clean operation without arguments don't remove the patch
        Assert.assertTrue(cli.sendLine("installer clean --host=" + host, false));

        final Path secondaryCustomPatchMavenRepo = secondaryCustomPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(Files.exists(secondaryCustomPatchDir) && Files.isDirectory(secondaryCustomPatchDir));
        Assert.assertTrue(secondaryCustomPatchDir + " does not contain the expected maven repository",
                Files.list(secondaryCustomPatchMavenRepo).allMatch(p -> secondaryCustomPatchMavenRepo.relativize(p).toString().equals("artifact")));

        // remove the custom patch
        Assert.assertTrue(cli.sendLine("installer clean --custom-patch --host=" + host, false));
        Assert.assertFalse(Files.exists(secondaryCustomPatchMavenRepo));
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
}
