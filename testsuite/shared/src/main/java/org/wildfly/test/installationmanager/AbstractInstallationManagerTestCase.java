/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.installationmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ManifestVersion;
import org.wildfly.installationmanager.Repository;

/**
 * Base test class to verify the high-level Installation Manager commands in standalone and domain mode environments. It uses a mocked implementation of the
 * installation manager, which provides dummy data for the test.
 * <p>
 * The purpose of this test is to ensure that the high-level commands, which rely on low-level management operations, can
 * retrieve the data from the mocked implementation.
 * <p>
 * See InstMgrResourceTestCase for low-level management operation unit testing.
 * <p>
 * For the installation manager high level commands, the only difference between standalone and domain mode is that commands in domain mode needs to use
 * --host=HOSTNAME to specify the target host where the operation will be executed. This abstract test class provides a method getHostSuffix(String)
 * that based on the test implementation, whether we are targeting to standalone or domain mode, it will return the expected suffix. In Standalone mode the suffix is an empty string.
 * Test that implements this abstract test provides an implementation of getServerDirs() as way to specify what are the hosts available for the operation under test.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class AbstractInstallationManagerTestCase extends AbstractCliTestBase {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    protected static final String MODULE_NAME = "org.jboss.prospero";
    protected static TestModule testModule;
    protected static final String DEFAULT_HOST = "";
    protected Path testRepoOnePath;
    protected Path testRepoTwoPath;

    static final Path TARGET_DIR = Paths.get(System.getProperty("basedir", ".")).resolve("target");


    @Before
    public void copyRepositories() throws Exception {
        final Path reposPath = temp.newFolder("repos").toPath();
        testRepoOnePath = reposPath.resolve("test-repo-one");
        testRepoTwoPath = reposPath.resolve("test-repo-two");

        copyRepository("test-repo-one", reposPath);
        copyRepository("test-repo-two", reposPath);

        // Always reinitialize our TestInstallationManager mock copy so we can use it
        // to assert the expected values provided by the copy running on the servers
        TestInstallationManager.initialize();
    }

    @After
    public void clean() throws IOException {
        for (String host : getHosts()) {
            // Clean any previous state
            assertTrue(cli.sendLine("installer clean " + getHostSuffix(host), false));
            assertFalse(Files.exists(getPreparedServerDir(host)));

        }

        for (File testZip : TARGET_DIR.toFile().listFiles((dir, name) -> name.startsWith("installation-manager") && name.endsWith(".zip"))) {
            Files.deleteIfExists(testZip.toPath());
        }
        TestInstallationManager.initialized = false;
    }


    public static String buildChannelOutput(Channel channel) {
        final String returnChar = Util.isWindows() ? "\r\n" : "\n";

        StringBuilder sb = new StringBuilder("-------").append(returnChar).append("# " + channel.getName()).append(returnChar);

        if (channel.getManifestUrl().isPresent()) {
            sb.append("  manifest: ").append(channel.getManifestUrl().get()).append(returnChar);
        } else if (channel.getManifestCoordinate().isPresent()) {
            sb.append("  manifest: ").append(channel.getManifestCoordinate().get()).append(returnChar);
        }

        sb.append("  repositories:").append(returnChar);

        for (Repository repository : channel.getRepositories()) {
            sb.append("    id: ").append(repository.getId()).append(returnChar).append("    url: ").append(repository.getUrl()).append(returnChar);
        }

        return sb.toString();
    }

    @Test
    public void _a_listChannel() {
        StringBuilder expected = new StringBuilder();
        for (Channel channel : TestInstallationManager.lstChannels) {
            expected.append(buildChannelOutput(channel));
        }
        expected.append("-------");

        for (String host : getHosts()) {
            cli.sendLine("installer channel-list " + getHostSuffix(host));
            String output = cli.readOutput();

            Assert.assertEquals(expected.toString(), output);
        }
    }

    @Test
    public void _b_addChannel() throws IOException {

        for (String host : getHosts()) {
            final ServerChannel serverChannel = getServerChannels().get(host);
            String channelName = serverChannel.channelName;
            String manifestGavOrUrl = serverChannel.manifestGavOrUrl;
            String repoStr = serverChannel.repoStr;

            cli.sendLine(
                    "installer channel-add --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + getHostSuffix(host));
            String output = cli.readOutput();
            Assert.assertEquals("Channel '" + channelName + "' created.", output);

            cli.sendLine("installer channel-list" + getHostSuffix(host));
            output = cli.readOutput();

            StringBuilder expected = new StringBuilder();

            TestInstallationManager.initialize();
            for (Channel channel : TestInstallationManager.lstChannels) {
                expected.append(buildChannelOutput(channel));
            }

            Repository repository = new Repository("id0", repoStr);
            final String manifestLocation = manifestGavOrUrl.contains(":") ? manifestGavOrUrl : Path.of(manifestGavOrUrl).toUri().toURL().toExternalForm();
            Channel newChannel = new Channel(channelName, List.of(repository), manifestLocation);
            expected.append(buildChannelOutput(newChannel));
            expected.append("-------");

            Assert.assertEquals(expected.toString(), output);
        }
    }

    @Test
    public void _c_removeChannel() {
        for (String host : getHosts()) {
            final ServerChannel serverChannel = getServerChannels().get(host);

            String channelName = serverChannel.removedChannelName;
            cli.sendLine("installer channel-remove --channel-name=" + channelName + getHostSuffix(host));
            String output = cli.readOutput();
            assertEquals("Channel '" + channelName + "' has been successfully removed.", output);
        }
    }

    @Test
    public void _d_editChannel() {
        for (String host : getHosts()) {
            final ServerChannel serverChannel = getServerChannels().get(host);
            String channelName = serverChannel.editedChannelName;
            String manifestGavOrUrl = serverChannel.editedManifestGavOrUrl;
            String repoStr = serverChannel.editedRepoStr;

            cli.sendLine(
                    "installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + getHostSuffix(host));
            String output = cli.readOutput();
            Assert.assertEquals("Channel '" + channelName + "' has been modified.", output);

            cli.sendLine("installer channel-list" + getHostSuffix(host));
            output = cli.readOutput();

            Repository repository = new Repository("id0", repoStr);
            Channel newChannel = new Channel(channelName, List.of(repository), manifestGavOrUrl);
            String expected = buildChannelOutput(newChannel);
            Assert.assertTrue(output, output.contains(expected));
        }
    }

    @Test
    public void _e_editChannelNotExist() {
        String host = getPrimaryOrDefaultHost();

        String channelName = "unknown";
        String manifestGavOrUrl = "group:artifact:edited-1.0.0.Final";
        String repoStr = "https://edited.com";

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer channel-edit --channel-name=" + channelName + " --manifest=" + manifestGavOrUrl + " --repositories=" + repoStr + getHostSuffix(host));
        });

        String expectedMessage = "Channel '" + channelName + "' is not present.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void _f_testHistory() {
        for (String host : getHosts()) {
            cli.sendLine("installer history " + getHostSuffix(host));
            String output = cli.readOutput();

            String[] lines = output.split("\n");
            assertEquals(4, lines.length);

            Set<String> expectedChangeKeys = TestInstallationManager.history.keySet();
            Set<String> expectedVersionsWithoutDescription = TestInstallationManager.nullDescriptionMV.stream()
                    .map(mv -> String.format("%s:%s", mv.getChannelId(), mv.getVersion()))
                    .collect(Collectors.toSet());
            List<String> expectedVersionsWithDescriptions = TestInstallationManager.descriptionMV.stream().map(ManifestVersion::getDescription).toList();

            Set<String> actualResults = new HashSet<>(Arrays.asList(lines));
            for (String actualResult : actualResults) {
                for (Iterator<String> it = expectedChangeKeys.iterator(); it.hasNext(); ) {
                    String expectedChangeKey = it.next();
                    if (actualResult.contains(expectedChangeKey)) {
                        switch (expectedChangeKey) {
                            case "update": {
                                for (String expected : expectedVersionsWithoutDescription) {
                                    assertTrue(actualResult.contains(expected));
                                }
                                break;
                            }
                            case "install": {
                                for (String expected : expectedVersionsWithDescriptions) {
                                    assertTrue(actualResult.contains(expected));
                                }
                                break;
                            }
                            case "rollback":
                            case "config_change": {
                                for (String expected : expectedVersionsWithDescriptions) {
                                    assertFalse(actualResult.contains(expected));
                                }
                                for (String expected : expectedVersionsWithoutDescription) {
                                    assertFalse(actualResult.contains(expected));
                                }
                            }
                        }
                        it.remove();
                        break;
                    }
                }
            }
            Assert.assertTrue(Arrays.asList(lines).toString(), expectedChangeKeys.isEmpty());
        }
    }

    @Test
    public void testRevisionDetails() {
        for (String host : getHosts()) {
            cli.sendLine("installer history --revision=dummy " + getHostSuffix(host));
            String output = cli.readOutput();

            assertTrue(output, output.contains("org.test.groupid1:org.test.artifact1.installed"));
            assertTrue(output, output.contains("org.test.groupid1:org.test.artifact1.removed"));
            assertTrue(output, output.contains("org.test.groupid1:org.test.artifact1.updated"));
            assertTrue(output, output.contains("[Added channel] channel-test-0"));
            assertTrue(output, output.contains("[Removed channel] channel-test-0"));
            assertTrue(output, output.contains("[Updated channel] channel-test-0"));
        }
    }

    @Test
    public void testCreateSnapShot() throws IOException {
        String hostPathAddress = "";

        for (String host : getHosts()) {
            if (!host.equals(DEFAULT_HOST)) {
                hostPathAddress = "/host=" + host;
            }

            Path exportPath = TARGET_DIR.normalize().toAbsolutePath().resolve("generated.zip");
            try {
                cli.sendLine("attachment save --operation=" + hostPathAddress + "/core-service=installer:clone-export() --file=" + exportPath);
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
    }

    @Test
    public void updateWithDryRun() {
        for (String host : getHosts()) {
            cli.sendLine("installer update --dry-run " + getHostSuffix(host));
            String output = cli.readOutput();
            Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
            Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
            Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        }
    }

    @Test
    public void updateWithConfirm() throws IOException {
        // The server under test is a single installation; we start two host (primary/secondary) sharing the same server installation,
        // but we cannot update the same installation twice, so we only test this case for primary host or the default host that represents a standalone mode
        String host = getPrimaryOrDefaultHost();

        cli.sendLine("installer update --repositories=id0::http://localhost --confirm " + getHostSuffix(host));
        String output = cli.readOutput();

        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithConfirmRepositories() throws IOException {
        String host = getPrimaryOrDefaultHost();

        cli.sendLine("installer update --confirm --repositories=id0::http://localhost " + getHostSuffix(host));
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithConfirmLocalCache() throws IOException {
        String host = getPrimaryOrDefaultHost();

        cli.sendLine("installer update --confirm --local-cache=" + TARGET_DIR + " " + getHostSuffix(host));
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateLocalCacheWithUseDefaultLocalCache() {
        String host = getPrimaryOrDefaultHost();

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --local-cache=" + TARGET_DIR + " --use-default-local-cache " + getHostSuffix(host));
        });

        String expectedMessage = "WFLYIM0021:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));

        exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --local-cache=" + TARGET_DIR + " --use-default-local-cache " + getHostSuffix(host));
        });

        expectedMessage = "WFLYIM0021:";
        actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void cannotUseConfirmAndDryRunAtTheSameTime() {
        String host = getPrimaryOrDefaultHost();
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --confirm --dry-run " + getHostSuffix(host));
        });

        String expectedMessage = "confirm and dry-run cannot be used at the same time.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void updateWithConfirmUsingMavenZipFile() throws IOException {
        String host = getPrimaryOrDefaultHost();
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        cli.sendLine("installer update --confirm --maven-repo-files=" + target + " " + getHostSuffix(host));
        String output = cli.readOutput();
        verifyUpdatePrepareDirWasCreated(getPreparedServerDir(host), output);
    }

    @Test
    public void updateWithConfirmUsingMultipleMavenZipFiles() throws IOException {
        String host = getPrimaryOrDefaultHost();
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        zipDir(testRepoTwoPath.toAbsolutePath(), targetTwo);

        cli.sendLine("installer update --confirm --maven-repo-files=" + targetOne + "," + targetTwo + " " + getHostSuffix(host));
        String output = cli.readOutput();
        verifyUpdatePrepareDirWasCreated(getPreparedServerDir(host), output);
    }

    @Test
    public void updateWithDryRunMavenZipFile() throws IOException {
        String host = getPrimaryOrDefaultHost();
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        cli.sendLine("installer update --dry-run --maven-repo-files=" + target + " " + getHostSuffix(host));
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertFalse(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertFalse(Files.exists(getPreparedServerDir(host)));
    }

    @Test
    public void updateWithDryRunMultipleMavenZipFile() throws IOException {
        String host = getPrimaryOrDefaultHost();
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        zipDir(testRepoTwoPath.toAbsolutePath(), targetTwo);

        cli.sendLine("installer update --dry-run --maven-repo-files=" + targetOne + "," + targetTwo + " " + getHostSuffix(host));
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertFalse(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertFalse(Files.exists(getPreparedServerDir(host)));
    }

    @Test
    public void updateUsingBlockingTimeout() throws IOException {
        String host = getPrimaryOrDefaultHost();
        assertTrue(cli.sendLine("installer update --confirm --headers={blocking-timeout=100} " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        String host = getPrimaryOrDefaultHost();

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --repositories=id0::http://localhost --maven-repo-files=" + target + " " + getHostSuffix(host));
        });

        String expectedMessage = "WFLYIM0012:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void updateWithConfirmToManifestVersion() throws IOException {
        // The server under test is a single installation; we start two host (primary/secondary) sharing the same server installation,
        // but we cannot update the same installation twice, so we only test this case for primary host or the default host that represents a standalone mode
        String host = getPrimaryOrDefaultHost();

        cli.sendLine("installer update --repositories=id0::http://localhost --confirm --manifest-versions=test-channel::1.0.1 " + getHostSuffix(host));
        String output = cli.readOutput();

        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));

        String markerContents = getMarkerContents(getPreparedServerDir(host));
        Assert.assertTrue(markerContents.contains("test-channel::1.0.1"));
        Assert.assertTrue(markerContents.contains("id0::http://localhost"));
    }

    @Test
    public void revertWithMavenZipFile() throws IOException {
        String host = getPrimaryOrDefaultHost();

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-files=" + target + " " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithMultipleMavenZipFiles() throws IOException {
        String host = getPrimaryOrDefaultHost();

        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        zipDir(testRepoTwoPath.toAbsolutePath(), targetTwo);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-files=" + targetOne + "," + targetTwo + " " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithNoResolveLocalMavenCache() throws IOException {
        String host = getPrimaryOrDefaultHost();

        assertTrue(cli.sendLine("installer revert --revision=dummy --no-resolve-local-cache " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithUseDefaultLocalCache() throws IOException {
        String host = getPrimaryOrDefaultHost();

        assertTrue(cli.sendLine("installer revert --revision=dummy --use-default-local-cache " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithLocalCacheMavenCache() throws IOException {
        String host = getPrimaryOrDefaultHost();

        assertTrue(cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertLocalCacheWithUseDefaultLocalCache() {
        String host = getPrimaryOrDefaultHost();

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " --use-default-local-cache " + getHostSuffix(host));
        });

        String expectedMessage = "WFLYIM0021:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));

        exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " --use-default-local-cache=true " + getHostSuffix(host));
        });

        expectedMessage = "WFLYIM0021:";
        actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertWithOffline() throws IOException {
        String host = getPrimaryOrDefaultHost();

        assertTrue(cli.sendLine("installer revert --revision=dummy --offline " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithHeaders() throws IOException {
        String host = getPrimaryOrDefaultHost();

        assertTrue(cli.sendLine("installer revert --revision=dummy --headers={blocking-timeout=100} " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithRepositories() throws IOException {
        String host = getPrimaryOrDefaultHost();

        assertTrue(cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void simpleRevert() throws IOException {
        String host = getPrimaryOrDefaultHost();

        assertTrue(cli.sendLine("installer revert --revision=dummy " + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(getPreparedServerDir(host)) && Files.isDirectory(getPreparedServerDir(host)));
        Assert.assertTrue(getPreparedServerDir(host) + " does not contain the expected file marker",
                directoryOnlyContains(getPreparedServerDir(host), p -> getPreparedServerDir(host).relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertCannotBeUsedWithoutRevision() {
        String host = getPrimaryOrDefaultHost();

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert " + getHostSuffix(host));
        });

        String expectedMessage = "WFLYCTL0155:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        String host = getPrimaryOrDefaultHost();

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost --maven-repo-files=" + target + " " + getHostSuffix(host));
        });

        String expectedMessage = "WFLYIM0012:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void uploadAndRemoveCustomPatch() throws IOException {
        String host = getPrimaryOrDefaultHost();
        String patchManifestGA = "group:artifact";
        Path hostCustomPatchDir = getPrimaryCustomServerDir().resolve(patchManifestGA.replace(":", "_"));

        createAndUploadCustomPatch(patchManifestGA, getHostSuffix(host), hostCustomPatchDir, testRepoOnePath, "artifact-one");
        removeCustomPatch(patchManifestGA, getHostSuffix(host), hostCustomPatchDir);
    }

    @Test
    public void removeNonExistingCustomPatch() {
        String host = getPrimaryOrDefaultHost();
        String patchManifestGA = "group-unknown:artifact-unknown";
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer remove-custom-patch --manifest=" + patchManifestGA + " " + getHostSuffix(host));
        });
        String expectedMessage = "WFLYIM0020:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage, actualMessage.contains(expectedMessage));

    }

    @Test
    public void uploadAndRemoveMultipleCustomPatches() throws IOException {
        String host = getPrimaryOrDefaultHost();
        String patchManifestGA_1 = "group1:artifact1";
        Path hostCustomPatchDir_1 = getPrimaryCustomServerDir().resolve(patchManifestGA_1.replace(":", "_"));
        createAndUploadCustomPatch(patchManifestGA_1, getHostSuffix(host), hostCustomPatchDir_1, testRepoOnePath, "artifact-one");

        String patchManifestGA_2 = "group2:artifact2";
        Path hostCustomPatchDir_2 = getPrimaryCustomServerDir().resolve(patchManifestGA_2.replace(":", "_"));
        createAndUploadCustomPatch(patchManifestGA_2, getHostSuffix(host), hostCustomPatchDir_2, testRepoTwoPath, "artifact-two");

        removeCustomPatch(patchManifestGA_1, getHostSuffix(host), hostCustomPatchDir_1);

        // check we still have the second patch
        final Path customPatchMavenRepo = hostCustomPatchDir_2.resolve("maven-repository");
        Assert.assertTrue(Files.exists(customPatchMavenRepo) && Files.isDirectory(customPatchMavenRepo));
        Assert.assertTrue(hostCustomPatchDir_2 + " does not contain the expected maven repository",
                directoryOnlyContains(customPatchMavenRepo, p -> customPatchMavenRepo.relativize(p).toString().equals("artifact-two")));

        // remove the patch 2
        removeCustomPatch(patchManifestGA_2, getHostSuffix(host), hostCustomPatchDir_2);
    }


    @Test
    public void testProductInfoIncludesInstallerInfo() throws Exception {
        String hostPathAddr = "";
        for (String host : getHosts()) {
            if (!host.isEmpty()) {
                hostPathAddr = "/host=" + host;
            }

            cli.sendLine(hostPathAddr + ":product-info");

            ModelNode res = cli.readAllAsOpResult().getResponseNode();
            final List<ModelNode> summaries = res.get("result").asList();
            for (ModelNode summary : summaries) {
                summary = summary.get("summary");
                if (!summary.hasDefined("instance-identifier")) {
                    continue;
                }

                Assert.assertTrue(summary.toString(), summary.hasDefined("last-update-date"));
                Assert.assertTrue(summary.hasDefined("channel-versions"));
                ModelNode expectedResult = new ModelNode();
                TestInstallationManager.descriptionMV.forEach(manifestVersion -> expectedResult.add(manifestVersion.getDescription()));
                Assert.assertEquals(expectedResult.asList(), summary.get("channel-versions").asList());
            }
        }
    }


    @Test
    public void cannotShutDownIfNoServerPrepared() {
        String host = getPrimaryOrDefaultHost();
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("shutdown --perform-installation " + getHostSuffix(host));
        });
        String expectedMessage = getNoPreparedServerErrorCode();
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void listManifestVersions() throws Exception {
        // The server under test is a single installation; we start two host (primary/secondary) sharing the same server installation,
        // but we cannot update the same installation twice, so we only test this case for primary host or the default host that represents a standalone mode
        String host = getPrimaryOrDefaultHost();

        cli.sendLine("installer list-manifest-versions" + getHostSuffix(host));
        String output = cli.readOutput();

        Assert.assertTrue(output, output.contains("Channel name: test-channel"));
        Assert.assertTrue(output, output.contains("Manifest location: a:b"));
        Assert.assertTrue(output, output.contains("Current manifest version: 1.0.0 (Logical version 1.0.0)"));
        Assert.assertFalse(output, output.contains("- 0.0.9 (Logical version 0.0.9)"));
        Assert.assertTrue(output, output.contains("- 1.0.1 (Logical version 1.0.1)"));
    }

    @Test
    public void listManifestVersionsIncludingDowngrades() throws Exception {
        // The server under test is a single installation; we start two host (primary/secondary) sharing the same server installation,
        // but we cannot update the same installation twice, so we only test this case for primary host or the default host that represents a standalone mode
        String host = getPrimaryOrDefaultHost();

        cli.sendLine("installer list-manifest-versions --include-downgrades=true" + getHostSuffix(host));
        String output = cli.readOutput();

        Assert.assertTrue(output, output.contains("Channel name: test-channel"));
        Assert.assertTrue(output, output.contains("Manifest location: a:b"));
        Assert.assertTrue(output, output.contains("Current manifest version: 1.0.0 (Logical version 1.0.0)"));
        Assert.assertTrue(output, output.contains("- 0.0.9 (Logical version 0.0.9)"));
        Assert.assertTrue(output, output.contains("- 1.0.1 (Logical version 1.0.1)"));
    }

    protected String getNoPreparedServerErrorCode() {
        return "WFLYHC0218:";
    }

    /**
     * list of paths used in the tests. The key is the host name for Domain testing or DEFAULT_HOST for standalone
     * NOTE: the order of keys determines order of operations in some tests
     *
     * @return HashMap where the values are the ServerPaths
     */
    protected abstract LinkedHashMap<String, ServerPaths> getServerDirs();

    protected static void createTestModule() throws IOException {
        testModule = new TestModule(MODULE_NAME, "org.wildfly.installation-manager.api");

        final URL serviceLoader = AbstractInstallationManagerTestCase.class.getClassLoader()
                .getResource("org/wildfly/test/installationmanager/services/org.wildfly.installationmanager.spi.InstallationManagerFactory");

        testModule.addResource("test-mock-installation-manager.jar")
                .addClass(TestInstallationManager.class)
                .addClass(TestInstallationManagerFactory.class)
                .addAsManifestResource(serviceLoader,
                        "services/org.wildfly.installationmanager.spi.InstallationManagerFactory");

        testModule.create(true);
    }

    protected List<String> getHosts() {
        return new ArrayList<>(getServerDirs().keySet());
    }

    /**
     * Returns the primary host or the DEFAULT_HOST representative host on standalone mode
     */
    private String getPrimaryOrDefaultHost() {
        return getHosts().get(0).trim();
    }

    private String getSecondaryHost() {
        if (getHosts().size() == 1) {
            return getPrimaryOrDefaultHost();
        } else {
            return getHosts().get(1).trim();
        }
    }

    protected static String getHostSuffix(String host) {
        final String hostSuffix;
        if (host == null || host.equals(DEFAULT_HOST)) {
            hostSuffix = "";
        } else {
            hostSuffix = " --host=" + host.trim();
        }
        return hostSuffix;
    }

    protected Path getPrimaryPrepareServerDir() {
        return getPreparedServerDir(getPrimaryOrDefaultHost());
    }

    protected Path getPrimaryCustomServerDir() {
        return getServerDirs().get(getPrimaryOrDefaultHost()).customPatch;
    }

    protected Path getPreparedServerDir(String host) {
        Objects.requireNonNull(host);
        return getServerDirs().get(host).prepareServer;
    }

    protected static class ServerPaths {
        protected final Path prepareServer;
        protected final Path customPatch;

        public ServerPaths(Path prepareServer, Path customPatch) {
            this.prepareServer = prepareServer;
            this.customPatch = customPatch;
        }
    }

    /**
     * Copy the maven repositories crafted for testing to the target temporal dir used by this test execution
     */
    protected static void copyRepository(String name, Path target) throws URISyntaxException, IOException {
        final URL repoUrl = AbstractInstallationManagerTestCase.class.getClassLoader().getResource("org/wildfly/test/installationmanager/" + name);

        if ("jar".equals(repoUrl.getProtocol())) {
            String repoPath = repoUrl.getFile().split("\\!")[1];

            copyFromJar(repoUrl.toURI(), repoPath, target.resolve(name));
        } else {
            FileUtils.copyDirectory(Path.of(repoUrl.toURI()).toFile(), target.resolve(name).toFile());
        }
    }

    protected static void copyFromJar(URI jar, String source, final Path target) throws IOException {
        try (FileSystem fileSystem = FileSystems.newFileSystem(
                jar,
                Collections.<String, String>emptyMap()
        )) {
            final Path jarPath = fileSystem.getPath(source);

            Files.walkFileTree(jarPath, new SimpleFileVisitor<Path>() {
                private Path currentTarget;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    currentTarget = target.resolve(jarPath.relativize(dir).toString());
                    Files.createDirectories(currentTarget);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, target.resolve(jarPath.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * A utility to associate channel data by host keys.
     */
    protected Map<String, ServerChannel> getServerChannels() {
        final HashMap<String, ServerChannel> channels = new HashMap<>();
        channels.put("primary", new ServerChannel(
                "test-primary", "group:artifact:primary-1.0.0.Final", "https://primary.com", "channel-test-1",
                "channel-test-2", "group:artifact:edited-1.0.0.Final", "https://edited.com"
        ));
        channels.put("secondary", new ServerChannel(
                "test-secondary", "/test", "https://secondary.com", "channel-test-2",
                "channel-test-1", "group:artifact:edited-1.0.0.Final", "https://edited.com"
        ));
        channels.put("", new ServerChannel(
                "test-standalone", "group:artifact:standalone-1.0.0.Final", "https://standalone.com", "channel-test-1",
                "channel-test-2", "group:artifact:edited-1.0.0.Final", "https://edited.com"
        ));

        return channels;
    }

    protected static class ServerChannel {
        final String channelName;
        final String manifestGavOrUrl;
        final String repoStr;
        final String removedChannelName;
        final String editedChannelName;
        final String editedManifestGavOrUrl;
        final String editedRepoStr;

        public ServerChannel(String channelName, String manifestGavOrUrl, String repoStr, String removedChannelName,
                             String editedChannelName, String editedManifestGavOrUrl, String editedRepoStr) {
            this.channelName = channelName;
            this.manifestGavOrUrl = manifestGavOrUrl;
            this.repoStr = repoStr;
            this.removedChannelName = removedChannelName;
            this.editedChannelName = editedChannelName;
            this.editedManifestGavOrUrl = editedManifestGavOrUrl;
            this.editedRepoStr = editedRepoStr;
        }
    }

    protected static boolean directoryOnlyContains(Path directory, Predicate<Path> match) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.allMatch(match);
        }
    }

    protected String getCauseLogFailure(String description, String expectedLogCode) {
        return "Unexpected Error Code. Got " + description + " It was expected: " + expectedLogCode;
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

    public void verifyUpdatePrepareDirWasCreated(Path expectedPreparedServerDir, String output) throws IOException {
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertTrue(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertTrue(Files.exists(expectedPreparedServerDir) && Files.isDirectory(expectedPreparedServerDir));
        Assert.assertTrue(expectedPreparedServerDir + " does not contain the expected file marker",
                directoryOnlyContains(expectedPreparedServerDir, p -> expectedPreparedServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    public void createAndUploadCustomPatch(String customPatchManifest, String hostSuffix, Path hostCustomPatchDir, Path mavenDirToZip, String expectedArtifact) throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(mavenDirToZip.toAbsolutePath(), target);

        // verify the patch doesn't exist yet
        final Path customPatchMavenRepo = hostCustomPatchDir.resolve("maven-repository");
        Assert.assertFalse(Files.exists(customPatchMavenRepo));

        Assert.assertTrue(
                cli.sendLine("installer upload-custom-patch --custom-patch-file=" + target + " --manifest=" + customPatchManifest + " " + hostSuffix, false));
        // verify clean operation without arguments don't remove the patch
        Assert.assertTrue(cli.sendLine("installer clean " + hostSuffix, false));
        Assert.assertTrue(Files.exists(customPatchMavenRepo) && Files.isDirectory(customPatchMavenRepo));
        Assert.assertTrue(hostCustomPatchDir + " does not contain the expected artifact included in the custom patch Zip file",
                directoryOnlyContains(customPatchMavenRepo, p -> customPatchMavenRepo.relativize(p).toString().equals(expectedArtifact)));
    }

    public void removeCustomPatch(String customPatchManifest, String hostSuffix, Path hostCustomPatchDir) {
        // remove the custom patch
        final Path primaryCustomPatchMavenRepo = hostCustomPatchDir.resolve("maven-repository");
        Assert.assertTrue(cli.sendLine("installer remove-custom-patch --manifest=" + customPatchManifest + " " + hostSuffix, false));
        Assert.assertFalse(Files.exists(primaryCustomPatchMavenRepo));
    }

    protected void testHostParameter(String hostArgument, Consumer<String> acceptor) {
        final List<String> commands = List.of("update", "clean", "revert", "history", "channel-list",
                "list-manifest-versions",
                "channel-add --channel-name test --manifest test --repositories test",
                "channel-edit --channel-name test --manifest test --repositories test",
                "channel-remove --channel-name test",
                "upload-custom-patch --custom-patch-file=dummy --manifest=manifest");

        for (String command : commands) {
            AssertionError exception = assertThrows(AssertionError.class, () ->
                    cli.sendLine("installer " + command + " " + hostArgument)
            );

            acceptor.accept(exception.getMessage());
        }
    }

    protected String getMarkerContents(Path serverDir) throws IOException {
        try (Stream<Path> stream = Files.walk(serverDir)) {
            Optional<Path> markerFile = stream.filter(p -> p.getFileName().toString().startsWith("server-prepare-marker-")).findAny();
            Assert.assertTrue(markerFile.isPresent());
            return Files.readString(markerFile.get());
        }
    }
}

