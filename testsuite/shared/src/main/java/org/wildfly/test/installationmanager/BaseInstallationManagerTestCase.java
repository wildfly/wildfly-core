/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.installationmanager;

import static org.junit.Assert.assertEquals;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.Repository;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class BaseInstallationManagerTestCase  extends AbstractCliTestBase {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    String MAVEN_REPO_DIR_NAME_IN_ZIP_FILES = "maven-repository";

    protected static final String MODULE_NAME = "org.jboss.prospero";
    protected static final String DEFAULT_HOST = "";
    protected static TestModule testModule;
    protected Path testRepoOnePath;
    protected Path testRepoTwoPath;

    protected static final Path TARGET_DIR = Paths.get(System.getProperty("basedir", ".")).resolve("target");

    @Before
    public void copyRepositories() throws Exception {
        final Path reposPath = temp.newFolder("repos").toPath();
        testRepoOnePath = reposPath.resolve("test-repo-one");
        testRepoTwoPath = reposPath.resolve("test-repo-two");

        copyRepository("test-repo-one", reposPath);
        copyRepository("test-repo-two", reposPath);
    }

    @After
    public void clean() throws IOException {
        for (String host : getHosts()) {
            // Clean any previous state
            assertTrue(cli.sendLine("installer clean" + getHostSuffix(host), false));
            Assert.assertTrue(!Files.exists(getPreparedServerDir(host)));

        }

        for(File testZip : TARGET_DIR.toFile().listFiles((dir, name) -> name.startsWith("installation-manager") && name.endsWith(".zip"))) {
            Files.deleteIfExists(testZip.toPath());
        }
    }

    @Test
    public void _a_listChannel() throws Exception {
        TestInstallationManager.initialize();
        StringBuilder expected = new StringBuilder();
        for (Channel channel : TestInstallationManager.lstChannels) {
            expected.append(buildChannelOutput(channel));
        }
        expected.append("-------");

        for (String host : getHosts()) {
            cli.sendLine("installer channel-list" + getHostSuffix(host));
            String output = cli.readOutput();

            Assert.assertEquals(expected.toString(), output);
        }
    }

    @Test
    public void _b_addChannel() throws IOException {

        for (String host : getHosts()) {
            final ServerChannel serverChannel = getServerChannels().get(host.equals(DEFAULT_HOST) ? "primary" : host);
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
        for (String host: getHosts()) {
            final ServerChannel serverChannel = getServerChannels().get(host.equals(DEFAULT_HOST) ? "primary" : host);

            String channelName = serverChannel.removedChannelName;
            cli.sendLine("installer channel-remove --channel-name=" + channelName + getHostSuffix(host));
            String output = cli.readOutput();
            assertEquals("Channel '" + channelName + "' has been successfully removed.", output);
        }
    }

    @Test
    public void _d_editChannel() {
        for (String host: getHosts()) {
            final ServerChannel serverChannel = getServerChannels().get(host.equals(DEFAULT_HOST) ? "primary" : host);
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
        String host = getPrimaryHost();

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
    public void _f_testHistory() throws IOException {
        String host = getSecondaryHost();
        cli.sendLine("installer history" + getHostSuffix(host));
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
        cli.sendLine("installer history --revision=dummy" + getHostSuffix(getPrimaryHost()));
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
        String host = getPrimaryHost();
        Path exportPath = TARGET_DIR.normalize().toAbsolutePath().resolve("generated.zip");
        try {
            final String operationPrefix;
            if (host == null || host.equals(DEFAULT_HOST)) {
                operationPrefix = "";
            } else {
                operationPrefix = "/host=" + host;
            }
            cli.sendLine("attachment save --operation=" + operationPrefix + "/core-service=installer:clone-export() --file=" + exportPath);
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
        String host = getPrimaryHost();
        cli.sendLine("installer update --dry-run" + getHostSuffix(host));
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
    }

    @Test
    public void updateWithConfirm() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        cli.sendLine("installer update --repositories=id0::http://localhost --confirm" + getHostSuffix(host));
        String output = cli.readOutput();

        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir,p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithConfirmRepositories() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        cli.sendLine("installer update --confirm --repositories=id0::http://localhost" + getHostSuffix(host));
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateWithConfirmLocalCache() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        cli.sendLine("installer update --confirm --local-cache=" + TARGET_DIR + getHostSuffix(host));
        String output = cli.readOutput();
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateLocalCacheWithUseDefaultLocalCache() {
        String host = getPrimaryHost();

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --local-cache=" + TARGET_DIR + " --use-default-local-cache" + getHostSuffix(host));
        });

        String expectedMessage = "WFLYIM0021:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));

        exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --local-cache=" + TARGET_DIR + " --use-default-local-cache" + getHostSuffix(host));
        });

        expectedMessage = "WFLYIM0021:";
        actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void cannotUseConfirmAndDryRunAtTheSameTime() {
        String host = getPrimaryHost();
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --confirm --dry-run" + getHostSuffix(host));
        });

        String expectedMessage = "confirm and dry-run cannot be used at the same time.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    @Test
    public void updateWithConfirmUsingMavenZipFile() throws Exception {
        String host = getPrimaryHost();
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        cli.sendLine("installer update --confirm --maven-repo-files=" + target + getHostSuffix(host));
        String output = cli.readOutput();
        verifyUpdatePrepareDirWasCreated(host, output);
    }

    @Test
    public void updateWithConfirmUsingMultipleMavenZipFiles() throws IOException {
        String host = getPrimaryHost();
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), targetTwo);

        cli.sendLine("installer update --confirm --maven-repo-files=" + targetOne + "," + targetTwo + getHostSuffix(host));
        String output = cli.readOutput();
        verifyUpdatePrepareDirWasCreated(host, output);
    }

    @Test
    public void updateWithDryRunMavenZipFile() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        cli.sendLine("installer update --dry-run --maven-repo-files=" + target + getHostSuffix(host));
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
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), targetTwo);

        cli.sendLine("installer update --dry-run --maven-repo-files=" + targetOne +","+ targetTwo + getHostSuffix(host));
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
        String host = getSecondaryHost();
        final Path secondaryPrepareServerDir = getPreparedServerDir(host);

        assertTrue(cli.sendLine("installer update --confirm --headers={blocking-timeout=100}" + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(secondaryPrepareServerDir) && Files.isDirectory(secondaryPrepareServerDir));
        Assert.assertTrue(secondaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(secondaryPrepareServerDir, p -> secondaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void updateCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        String host = getPrimaryHost();

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer update --repositories=id0::http://localhost --maven-repo-files=" + target + getHostSuffix(host));
        });

        String expectedMessage = "WFLYIM0012:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertWithMavenZipFile() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-files=" + target + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithMultipleMavenZipFiles() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        zipDir(testRepoTwoPath.toAbsolutePath(), targetTwo);

        Assert.assertTrue(cli.sendLine("installer revert --revision=dummy --maven-repo-files=" + targetOne + "," + targetTwo + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithNoResolveLocalMavenCache() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        assertTrue(cli.sendLine("installer revert --revision=dummy --no-resolve-local-cache" + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
            directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithUseDefaultLocalCache() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        assertTrue(cli.sendLine("installer revert --revision=dummy --use-default-local-cache" + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithLocalCacheMavenCache() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        assertTrue(cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertLocalCacheWithUseDefaultLocalCache() {
        String host = getPrimaryHost();

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " --use-default-local-cache" + getHostSuffix(host));
        });

        String expectedMessage = "WFLYIM0021:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));

        exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --local-cache=" + TARGET_DIR + " --use-default-local-cache=true" + getHostSuffix(host));
        });

        expectedMessage = "WFLYIM0021:";
        actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertWithOffline() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        assertTrue(cli.sendLine("installer revert --revision=dummy --offline" + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithHeaders() throws IOException {
        String host = getSecondaryHost();
        Path secondaryPrepareServerDir = getPreparedServerDir(host);

        assertTrue(cli.sendLine("installer revert --revision=dummy --headers={blocking-timeout=100}" + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(secondaryPrepareServerDir) && Files.isDirectory(secondaryPrepareServerDir));
        Assert.assertTrue(secondaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(secondaryPrepareServerDir, p -> secondaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertWithRepositories() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        assertTrue(cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost" + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void simpleRevert() throws IOException {
        String host = getPrimaryHost();
        Path primaryPrepareServerDir = getPrimaryPrepareServerDir();

        assertTrue(cli.sendLine("installer revert --revision=dummy" + getHostSuffix(host), false));

        Assert.assertTrue(Files.exists(primaryPrepareServerDir) && Files.isDirectory(primaryPrepareServerDir));
        Assert.assertTrue(primaryPrepareServerDir + " does not contain the expected file marker",
                directoryOnlyContains(primaryPrepareServerDir, p -> primaryPrepareServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    @Test
    public void revertCannotBeUsedWithoutRevision() {
        String host = getPrimaryHost();

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert" + getHostSuffix(host));
        });

        String expectedMessage = "WFLYCTL0155:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void revertCannotBeUsedWithMavenRepoFileAndRepositories() throws IOException {
        String host = getPrimaryHost();

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(testRepoOnePath.toAbsolutePath(), target);

        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer revert --revision=dummy --repositories=id0::http://localhost --maven-repo-files=" + target + getHostSuffix(host));
        });

        String expectedMessage = "WFLYIM0012:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(getCauseLogFailure(actualMessage, expectedMessage), actualMessage.contains(expectedMessage));
    }

    @Test
    public void uploadAndRemoveCustomPatch() throws IOException {
        String patchManifestGA = "group:artifact";
        String host = getPrimaryHost();
        Path hostCustomPatchDir = getPrimaryCustomServerDir().resolve(patchManifestGA.replace(":", "_"));

        createAndUploadCustomPatch(patchManifestGA, host, hostCustomPatchDir, testRepoOnePath, "artifact-one");
        removeCustomPatch(patchManifestGA, host, hostCustomPatchDir);
    }

    @Test
    public void removeNonExistingCustomPatch() {
        String host = getPrimaryHost();
        String patchManifestGA = "group-unknown:artifact-unknown";
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("installer remove-custom-patch --manifest=" + patchManifestGA + getHostSuffix(host));
        });
        String expectedMessage = "WFLYIM0020:";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage, actualMessage.contains(expectedMessage));

    }

    @Test
    public void uploadAndRemoveMultipleCustomPatches() throws IOException {
        String host = getPrimaryHost();
        Path primaryCustomPatchBaseDir = getPrimaryCustomServerDir();

        String patchManifestGA_1 = "group1:artifact1";
        Path hostCustomPatchDir_1 = primaryCustomPatchBaseDir.resolve(patchManifestGA_1.replace(":", "_"));
        createAndUploadCustomPatch(patchManifestGA_1, host, hostCustomPatchDir_1, testRepoOnePath, "artifact-one");

        String patchManifestGA_2 = "group2:artifact2";
        Path hostCustomPatchDir_2 = primaryCustomPatchBaseDir.resolve(patchManifestGA_2.replace(":", "_"));
        createAndUploadCustomPatch(patchManifestGA_2, host, hostCustomPatchDir_2, testRepoTwoPath, "artifact-two");

        removeCustomPatch(patchManifestGA_1, host, hostCustomPatchDir_1);

        // check we still have the second patch
        final Path customPatchMavenRepo = hostCustomPatchDir_2.resolve(MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(Files.exists(customPatchMavenRepo) && Files.isDirectory(customPatchMavenRepo));
        Assert.assertTrue(hostCustomPatchDir_2 + " does not contain the expected maven repository",
                directoryOnlyContains(customPatchMavenRepo, p -> customPatchMavenRepo.relativize(p).toString().equals("artifact-two")));

        // remove the patch 2
        removeCustomPatch(patchManifestGA_2, host, hostCustomPatchDir_2);
    }

    @Test
    public void cannotShutDownIfNoServerPrepared() {
        String host = getPrimaryHost();
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            cli.sendLine("shutdown --perform-installation" + getHostSuffix(host));
        });
        String expectedMessage = getNoPreparedServerErrorCode();
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }

    protected abstract String getNoPreparedServerErrorCode();

    @Test
    public void testProductInfoIncludesInstallerInfo() throws Exception {
        cli.sendLine(":product-info");

        ModelNode res = cli.readAllAsOpResult().getResponseNode();

        final List<ModelNode> hostResults = res.get("result").asList();
        for (ModelNode host : hostResults) {
            final ModelNode summaryNode = host.get("result");
            final List<ModelNode> summaries;
            if (summaryNode.getType() == ModelType.LIST) {
                summaries = summaryNode.asList();
            } else {
                summaries = List.of(summaryNode);
            }
            for (ModelNode summary : summaries) {
                summary = summary.get("summary");
                if (!summary.hasDefined("instance-identifier")) {
                    continue;
                }

                Assert.assertTrue(summary.toString(), summary.hasDefined("last-update-date"));
                Assert.assertTrue(summary.hasDefined("channel-versions"));
                Assert.assertEquals(List.of(new ModelNode("Update 1")), summary.get("channel-versions").asList());
            }
        }
    }

    private String getPrimaryHost() {
        return getHosts().get(0).trim();
    }

    private String getSecondaryHost() {
        if (getHosts().size() == 1) {
            return getPrimaryHost();
        } else {
            return getHosts().get(1).trim();
        }
    }

    private static String getHostSuffix(String host) {
        final String hostSuffix;
        if (host == null || host.equals(DEFAULT_HOST)) {
            hostSuffix = "";
        } else {
            hostSuffix = " --host=" + host.trim();
        }
        return hostSuffix;
    }

    protected List<String> getHosts() {
        return new ArrayList<>(getServerDirs().keySet());
    }

    protected Path getPrimaryPrepareServerDir() {
        return getPreparedServerDir(getPrimaryHost());
    }

    protected Path getPrimaryCustomServerDir() {
        return getServerDirs().get(getPrimaryHost()).customPatch;
    }

    private Path getPreparedServerDir(String host) {
        Objects.requireNonNull(host);
        return getServerDirs().get(host).prepareServer;
    }

    /**
     * list of paths used in the tests. The key is the host name for Domain testing or DEFAULT_HOST for standalone
     * NOTE: the order of keys determines order of operations in some tests
     *
     * @return
     */
    protected abstract LinkedHashMap<String, ServerPaths> getServerDirs();

    protected static void createTestModule() throws IOException {
        testModule = new TestModule(MODULE_NAME, "org.wildfly.installation-manager.api");

        final URL serviceLoader = BaseInstallationManagerTestCase.class.getClassLoader().getResource("org/wildfly/test/installationmanager/services/org.wildfly.installationmanager.spi.InstallationManagerFactory");
        testModule.addResource("test-mock-installation-manager.jar").addClass(TestInstallationManager.class).addClass(TestInstallationManagerFactory.class)
                .addAsManifestResource(serviceLoader,
                        "services/org.wildfly.installationmanager.spi.InstallationManagerFactory");
        testModule.create(true);
    }

    protected static String buildChannelOutput(Channel channel) {
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

    protected void testHostParameter(String suffix, Consumer<String> acceptor) {
        final List<String> commands = List.of("update", "clean", "revert", "history", "channel-list",
                "channel-add --channel-name test --manifest test --repositories test", "channel-edit --channel-name test --manifest test --repositories test",
                "channel-remove --channel-name test", "upload-custom-patch --custom-patch-file=dummy --manifest=manifest");

        final String trimmedSuffix = (suffix == null || suffix.equals(DEFAULT_HOST)) ? "" : " " + suffix.trim();

        for (String command : commands) {
            AssertionError exception = assertThrows(AssertionError.class, () ->
                    cli.sendLine("installer " + command + trimmedSuffix)
            );

            acceptor.accept(exception.getMessage());
        }
    }

    protected static boolean directoryOnlyContains(Path directory, Predicate<Path> match) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.allMatch(match);
        }
    }

    protected static String getCauseLogFailure(String description, String expectedLogCode) {
        return "Unexpected Error Code. Got " + description + " It was expected: " + expectedLogCode;
    }

    protected static void zipDir(Path sourcePath, Path target) throws IOException {
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

    protected void verifyUpdatePrepareDirWasCreated(String host, String output) throws IOException {
        final Path expectedPreparedServerDir = getPreparedServerDir(host);

        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.installed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.removed"));
        Assert.assertTrue(output, output.contains("org.findupdates:findupdates.updated"));
        Assert.assertTrue(output,
                output.contains("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command."));

        Assert.assertTrue(Files.exists(expectedPreparedServerDir) && Files.isDirectory(expectedPreparedServerDir));
        Assert.assertTrue(expectedPreparedServerDir + " does not contain the expected file marker",
                directoryOnlyContains(expectedPreparedServerDir, p -> expectedPreparedServerDir.relativize(p).toString().startsWith("server-prepare-marker-")));
    }

    protected void createAndUploadCustomPatch(String customPatchManifest, String host, Path hostCustomPatchDir, Path mavenDirToZip, String expectedArtifact) throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        zipDir(mavenDirToZip.toAbsolutePath(), target);

        // verify the patch doesn't exist yet
        final Path customPatchMavenRepo = hostCustomPatchDir.resolve(MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertFalse(Files.exists(customPatchMavenRepo));

        Assert.assertTrue(
                cli.sendLine("installer upload-custom-patch --custom-patch-file=" + target + " --manifest=" + customPatchManifest + getHostSuffix(host), false));
        // verify clean operation without arguments don't remove the patch
        Assert.assertTrue(cli.sendLine("installer clean" + getHostSuffix(host), false));
        Assert.assertTrue(Files.exists(customPatchMavenRepo) && Files.isDirectory(customPatchMavenRepo));
        Assert.assertTrue(hostCustomPatchDir + " does not contain the expected artifact included in the custom patch Zip file",
                directoryOnlyContains(customPatchMavenRepo, p -> customPatchMavenRepo.relativize(p).toString().equals(expectedArtifact)));
    }

    protected void removeCustomPatch(String customPatchManifest, String host, Path hostCustomPatchDir) {
        // remove the custom patch
        final Path primaryCustomPatchMavenRepo = hostCustomPatchDir.resolve(MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
        Assert.assertTrue(cli.sendLine("installer remove-custom-patch --manifest=" + customPatchManifest + getHostSuffix(host), false));
        Assert.assertFalse(Files.exists(primaryCustomPatchMavenRepo));
    }

    protected static void copyRepository(String name, Path target) throws URISyntaxException, IOException {
        final URL repoUrl = BaseInstallationManagerTestCase.class.getClassLoader().getResource("org/wildfly/test/installationmanager/" + name);

        System.out.println(repoUrl);
        if ("jar".equals(repoUrl.getProtocol())) {
            String repoPath = repoUrl.getFile().split("\\!")[1];

            copyFromJar(repoUrl.toURI(), repoPath, target.resolve(name));
        }else {
            FileUtils.copyDirectory(Path.of(repoUrl.toURI()).toFile(), target.resolve(name).toFile());
        }
    }

    protected static void copyFromJar(URI jar, String source, final Path target) throws URISyntaxException, IOException {
        System.out.println(jar);
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

    protected static class ServerPaths {
        protected final Path prepareServer;
        protected final Path customPatch;

        public ServerPaths(Path prepareServer, Path customPatch) {
            this.prepareServer = prepareServer;
            this.customPatch = customPatch;
        }
    }

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
}
