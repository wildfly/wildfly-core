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

package org.wildfly.test.installationmanager;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.InstallationChanges;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.OperationNotAvailableException;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;

/**
 * Mock Installation Manager API implementation to be used in the tests.
 */
public class TestInstallationManager implements InstallationManager {
    public static MavenOptions mavenOptions;
    public static Path installationDir;
    public static List<Channel> lstChannels;
    public static List<Repository> findUpdatesRepositories;
    public static List<ArtifactChange> findUpdatesChanges;
    public static List<Repository> prepareUpdatesRepositories;
    public static Path prepareUpdatesTargetDir;

    public static List<Repository> prepareRevertRepositories;
    public static Path prepareRevertTargetDir;
    public static boolean noUpdatesFound;
    public static InstallationChanges installationChanges;
    public static HashMap<String, HistoryResult> history;
    public static boolean initialized = false;

    public static String APPLY_REVERT_BASE_GENERATED_COMMAND = "apply revert";
    public static String APPLY_UPDATE_BASE_GENERATED_COMMAND = "apply update";

    public static void initialize() throws IOException {
        if (!initialized) {
            installationDir = null;
            mavenOptions = null;

            // Channels Sample Data
            lstChannels = new ArrayList<>();

            List<Repository> repoList = new ArrayList<>();
            repoList.add(new Repository("id0", "http://localhost"));
            repoList.add(new Repository("id1", "file://dummy"));
            lstChannels.add(new Channel("channel-test-0", repoList, "org.test.groupid:org.test.artifactid:1.0.0.Final"));

            repoList = new ArrayList<>();
            repoList.add(new Repository("id1", "file://dummy"));
            try {
                lstChannels.add(new Channel("channel-test-1", repoList, new URL("file://dummy")));
            } catch (MalformedURLException e) {
                // ignored
            }

            repoList = new ArrayList<>();
            repoList.add(new Repository("id0", "http://localhost"));
            repoList.add(new Repository("id2", "file://dummy"));
            lstChannels.add(new Channel("channel-test-2", repoList));

            // Changes Sample Data: Artifacts
            List<ArtifactChange> artifactChanges = new ArrayList<>();
            ArtifactChange installed = new ArtifactChange("1.0.0.Final", "1.0.1.Final", "org.test.groupid1:org.test.artifact1.installed",
                    ArtifactChange.Status.INSTALLED);
            ArtifactChange removed = new ArtifactChange("1.0.0.Final", "1.0.1.Final", "org.test.groupid1:org.test.artifact1.removed",
                    ArtifactChange.Status.REMOVED);
            ArtifactChange updated = new ArtifactChange("1.0.0.Final", "1.0.1.Final", "org.test.groupid1:org.test.artifact1.updated",
                    ArtifactChange.Status.UPDATED);
            artifactChanges.add(installed);
            artifactChanges.add(removed);
            artifactChanges.add(updated);

            // Changes Sample Data: Channels
            List<ChannelChange> channelChanges = new ArrayList<>();
            List<Repository> channelChangeBaseRepositories = new ArrayList<>();
            channelChangeBaseRepositories.add(new Repository("id0", "http://channelchange.com"));
            channelChangeBaseRepositories.add(new Repository("id1", "file://channelchange"));
            Channel channelChangeBase = new Channel("channel-test-0", channelChangeBaseRepositories,
                    "org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final");

            List<Repository> channelModifiedRepositories = new ArrayList<>();
            channelModifiedRepositories.add(new Repository("id0", "http://channelchange-modified.com"));
            channelModifiedRepositories.add(new Repository("id1-modified", "file://channelchange"));
            channelModifiedRepositories.add(new Repository("id1-added", "file://channelchange-added"));
            Channel channelChangeModified = new Channel("channel-test-0", channelModifiedRepositories,
                    "org.channelchange.groupid:org.channelchange.artifactid:1.0.1.Final");

            ChannelChange cChangeAdded = new ChannelChange(null, channelChangeBase, ChannelChange.Status.ADDED);
            ChannelChange cChangeRemoved = new ChannelChange(channelChangeBase, null, ChannelChange.Status.REMOVED);
            ChannelChange cChangeModified = new ChannelChange(channelChangeBase, channelChangeModified, ChannelChange.Status.MODIFIED);
            channelChanges.add(cChangeAdded);
            channelChanges.add(cChangeRemoved);
            channelChanges.add(cChangeModified);
            installationChanges = new InstallationChanges(artifactChanges, channelChanges);

            // History sample data
            history = new HashMap<>();
            history.put("update", new HistoryResult("update", Instant.now(), "update", "update description"));
            history.put("install", new HistoryResult("install", Instant.now(), "install", "install description"));
            history.put("rollback", new HistoryResult("rollback", Instant.now(), "rollback", "rollback description"));
            history.put("config_change", new HistoryResult("config_change", Instant.now(), "config_change", "config_change description"));

            // List Updates sample Data

            findUpdatesChanges = new ArrayList<>();
            findUpdatesChanges.add(new ArtifactChange("1.0.0.Final", "5.0.0.Final", "org.findupdates:findupdates.installed", ArtifactChange.Status.INSTALLED));
            findUpdatesChanges.add(new ArtifactChange("3.0.0.Final", "1.0.1.Final", "org.findupdates:findupdates.removed", ArtifactChange.Status.REMOVED));
            findUpdatesChanges.add(new ArtifactChange("1.0.0.Final", "1.0.1.Final", "org.findupdates:findupdates.updated", ArtifactChange.Status.UPDATED));

            findUpdatesRepositories = new ArrayList<>();

            noUpdatesFound = false;

            // prepare Updates sample data
            prepareUpdatesRepositories = new ArrayList<>();
            prepareUpdatesTargetDir = null;

            // prepare Revert sample data
            prepareRevertRepositories = new ArrayList<>();
            prepareRevertTargetDir = null;

            initialized = true;
        }
    }

    public TestInstallationManager(Path installationDir, MavenOptions mavenOptions) throws Exception {
        initialize();
        this.installationDir = installationDir;
        this.mavenOptions = mavenOptions;
    }

    @Override
    public List<HistoryResult> history() throws Exception {
        return new ArrayList<>(history.values());
    }

    @Override
    public InstallationChanges revisionDetails(String revision) throws Exception {
        return installationChanges;
    }

    @Override
    public void prepareRevert(String revision, Path targetDir, List<Repository> repositories) throws Exception {
        prepareRevertTargetDir = targetDir;
        prepareRevertRepositories = new ArrayList<>(repositories);
        Files.createTempFile(targetDir, "server-prepare-marker-", null);
    }

    @Override
    public boolean prepareUpdate(Path targetDir, List<Repository> repositories) throws Exception {
        if (noUpdatesFound) {
            return false;
        }
        prepareUpdatesTargetDir = targetDir;
        prepareUpdatesRepositories = new ArrayList<>(repositories);
        Files.createTempFile(targetDir, "server-prepare-marker-", null);
        return true;
    }

    @Override
    public List<ArtifactChange> findUpdates(List<Repository> repositories) throws Exception {
        if (noUpdatesFound) {
            return new ArrayList<>();
        }
        findUpdatesRepositories = new ArrayList<>(repositories);
        return findUpdatesChanges;
    }

    @Override
    public Collection<Channel> listChannels() throws Exception {
        return lstChannels;
    }

    @Override
    public void removeChannel(String channelName) throws Exception {
        for (Iterator<Channel> it = lstChannels.iterator(); it.hasNext();) {
            Channel c = it.next();
            if (c.getName().equals(channelName)) {
                it.remove();
            }
        }
    }

    @Override
    public void addChannel(Channel channel) throws Exception {
        lstChannels.add(channel);
    }

    @Override
    public void changeChannel(Channel channel) throws Exception {
        for (Iterator<Channel> it = lstChannels.iterator(); it.hasNext();) {
            Channel c = it.next();
            if (c.getName().equals(channel.getName())) {
                it.remove();
            }
        }
        lstChannels.add(channel);
    }

    @Override
    public Path createSnapshot(Path targetPath) throws Exception {
        if (!targetPath.toString().endsWith(".zip")) {
            throw new IllegalArgumentException("it is expected that the target path is a path of a Zip fie. Current target path is: " + targetPath);
        }
        Path source = Files.createTempFile("installation-manager-test-", ".tmp");
        zipDir(source, targetPath);
        Files.delete(source);
        return targetPath;
    }

    @Override
    public String generateApplyUpdateCommand(Path scriptHome, Path candidatePath) throws OperationNotAvailableException {
        return scriptHome + APPLY_UPDATE_BASE_GENERATED_COMMAND + candidatePath.toString();
    }

    @Override
    public String generateApplyRevertCommand(Path scriptHome, Path candidatePath) throws OperationNotAvailableException {
        return scriptHome + APPLY_REVERT_BASE_GENERATED_COMMAND + candidatePath.toString();
    }

    public static void zipDir(Path inputFile, Path target) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(target.toFile()); ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry(inputFile.getFileName().toString());
            zos.putNextEntry(entry);

            // read the input file and write its contents to the ZipOutputStream
            try (FileInputStream fis = new FileInputStream(inputFile.toFile())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
            zos.finish();
        }
    }
}
