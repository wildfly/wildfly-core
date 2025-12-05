/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.wildfly.core.instmgr.logging.InstMgrLogger.ROOT_LOGGER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jboss.as.controller.services.path.PathManager;
import org.jboss.logging.Logger;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.ManifestVersion;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * This is the main service used by the installation manager management operation handlers.
 */
class InstMgrService implements Service {
    private static final Logger LOG = Logger.getLogger(InstMgrService.class);

    private final InstallationManagerFactory imf;
    private final Supplier<PathManager> pathManagerSupplier;
    private final Consumer<InstMgrService> consumer;
    private final Supplier<Executor> executorSupplier;
    private PathManager pathManager;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private Path homeDir;
    private Path customPatchPath;
    private Path prepareServerPath;

    private Path controllerTempDir;
    private final ConcurrentMap<String, Path> tempDirs = new ConcurrentHashMap<>();
    private final InstMgrCandidateStatus candidateStatus;
    private Executor executor;

    InstMgrService(InstallationManagerFactory imf, Supplier<PathManager> pathManagerSupplier, Supplier<Executor> executorSupplier, Consumer<InstMgrService> consumer) {
        this.imf = imf;
        this.pathManagerSupplier = pathManagerSupplier;
        this.candidateStatus = new InstMgrCandidateStatus();
        this.executorSupplier = executorSupplier;
        this.consumer = consumer;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        this.pathManager = pathManagerSupplier.get();
        this.executor = executorSupplier.get();

        this.homeDir = Path.of(this.pathManager.getPathEntry("jboss.home.dir").resolvePath());

        this.controllerTempDir = Paths.get(pathManager.getPathEntry("jboss.controller.temp.dir").resolvePath());

        // Where we are going to store the prepared installations before applying them
        this.prepareServerPath = controllerTempDir.resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        // Where we are going to store the custom patch repositories
        this.customPatchPath = homeDir.resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);

        // Properties file used to send information to the launch scripts
        Path propertiesPath = homeDir.resolve("bin").resolve("installation-manager.properties");

        this.candidateStatus.initialize(propertiesPath);
        try {
            if (candidateStatus.getStatus() == InstMgrCandidateStatus.Status.PREPARING) {
                candidateStatus.setFailed();
            }
            // Ensure prepare server parent dir is ready
            Files.createDirectories(prepareServerPath.getParent());
        } catch (IOException e) {
            throw new StartException(e);
        }

        logInstallationStatus();

        started.set(true);
        this.consumer.accept(this);
    }

    @Override
    public void stop(StopContext context) {
        started.set(false);
        this.pathManager = null;
        try {
            deleteTempDirs();
        } catch (IOException e) {
            InstMgrLogger.ROOT_LOGGER.error(e);
        }
        consumer.accept(null);
    }

    Path createTempDir(String workDirPrefix) throws IOException {
        Path tempDirectory = Files.createTempDirectory(workDirPrefix);
        this.tempDirs.put(tempDirectory.getFileName().toString(), tempDirectory);

        return tempDirectory;
    }

    Path getTempDirByName(String workDirName) {
        return this.tempDirs.get(workDirName);
    }

    void deleteTempDirs() throws IOException {
        for (Iterator<Map.Entry<String, Path>> it = tempDirs.entrySet().iterator(); it.hasNext();) {
            Path workDir = it.next().getValue();
            try (Stream<Path> walk = Files.walk(workDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
            it.remove();
        }
    }

    void deleteTempDir(Path tempDirPath) throws IOException {
        if (tempDirPath == null) {
            return;
        }

        deleteTempDir(tempDirPath.getFileName().toString());
    }

    void deleteTempDir(String tempDirName) throws IOException {
        if (tempDirName == null) {
            return;
        }

        Path dirToClean = this.tempDirs.get(tempDirName);
        tempDirs.remove(tempDirName);
        if (dirToClean != null && dirToClean.toFile().exists()) {
            try (Stream<Path> walk = Files.walk(dirToClean)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private void checkStarted() throws IllegalStateException {
        if (!started.get())
            throw InstMgrLogger.ROOT_LOGGER.installationManagerServiceDown();
    }

    Path getHomeDir() throws IllegalStateException {
        checkStarted();
        return homeDir;
    }

    Path getCustomPatchDir(String manifestGav) throws IllegalStateException {
        checkStarted();
        return customPatchPath.resolve(manifestGav);
    }

    Path getPreparedServerDir() throws IllegalStateException {
        checkStarted();
        return prepareServerPath;
    }

    boolean canPrepareServer() {
        try {
            return this.candidateStatus.getStatus() == InstMgrCandidateStatus.Status.CLEAN;
        } catch (IOException e) {
            LOG.debug("Cannot load the prepared server status from a properties file", e);
            return false;
        }
    }

    void beginCandidateServer() throws IOException {
        this.candidateStatus.begin();
    }

    void commitCandidateServer(String command) throws IOException {
        this.candidateStatus.commit(command);
    }

    void resetCandidateStatus() throws IOException {
        this.candidateStatus.reset();
    }

    InstMgrCandidateStatus.Status getCandidateStatus() throws IOException {
        return this.candidateStatus.getStatus();
    }

    Path getControllerTempDir() {
        checkStarted();
        return controllerTempDir;
    }

    public Executor getMgmtExecutor() {
        return executor;
    }



    private void logInstallationStatus() {
        if (ROOT_LOGGER.isInfoEnabled()) {
            MavenOptions mavenOptions = new MavenOptions(null, false);
            InstallationManager installationManager = null;
            Collection<ManifestVersion> channels = null;
            try {
                installationManager = imf.create(homeDir, mavenOptions);
                channels = installationManager.getInstalledVersions();
                ROOT_LOGGER.provisioningChannels(getChannelVersions(channels));
            } catch (Exception e) {
                if (installationManager == null) {
                    ROOT_LOGGER.failedToCreateInstallationManager(homeDir, e);
                } else if (channels == null) {
                    ROOT_LOGGER.failedToFindInstallationChannels(e);
                }
            }
        }
    }

    private static String getChannelVersions(Collection<ManifestVersion> versions) {

        List<ManifestVersion> versionList = sortManifests(versions);

        int first = versionList.size() - 1;
        StringBuilder sb = new StringBuilder();
        for (int i = first; i >= 0; i--) {
            if (i != first) {
                sb.append(", ");
            }
            ManifestVersion mv = versionList.get(i);
            sb.append(mv.getChannelId()); // either a maven GA or a URL
            sb.append(':');
            sb.append(mv.getVersion()); // either a maven version or the hash of the URL content
            if (mv.getDescription() != null && !mv.getDescription().isEmpty()) {
                sb.append(" (");
                sb.append(mv.getDescription()); // the logical-version value in the manifest.yaml,
                                                // intended to be short identifier of what the channel provides
                                                // plus the version of that thing specified by the particular manifest version
                sb.append(')');
            }
        }
        return sb.toString();
    }

    /**
     * This is a non-guaranteed attempt to order the channels such that more 'important'
     * ones come first. Not guaranteed to work, as it assumes prospero internals.
     * The inputs we get from prospero put the maven manifests first, but with the
     * base channel later in the list of maven manifests. Then it provides URL manifests.
     *
     * @param input the manifest versions as provided by the installation manager
     * @return the sorted manifest versions
     */
    private static List<ManifestVersion> sortManifests(Collection<ManifestVersion> input) {
        List<ManifestVersion> versionList = new ArrayList<>(input.size());
        for (ManifestVersion mv : input) {
            if (mv.getType() == ManifestVersion.Type.MAVEN) {
                // later in the maven input list tends to be more important, so put it first
                versionList.add(0, mv);
            } else {
                // URL channels are atypical, so likely less fundamental and come later.
                versionList.add(mv);
            }
        }
        return versionList;
    }
}
