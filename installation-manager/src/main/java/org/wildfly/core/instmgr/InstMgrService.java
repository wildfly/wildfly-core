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

package org.wildfly.core.instmgr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jboss.as.controller.services.path.AbsolutePathService;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.logging.Logger;
import org.jboss.modules.PathUtils;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.core.instmgr.logging.InstMgrLogger;

/**
 * This is the main service used by the installation manager management operation handlers.
 */
class InstMgrService implements Service {
    private static final Logger LOG = Logger.getLogger(InstMgrService.class);
    private final Supplier<PathManager> pathManagerSupplier;
    private final Consumer<InstMgrService> consumer;
    private PathManager pathManager;
    private AtomicBoolean started = new AtomicBoolean(false);
    private Path homeDir;
    private Path propertiesPath;
    private Path customPatchPath;
    private Path prepareServerPath;
    private HashMap<String, Path> tempDirs = new HashMap<>();
    private InstMgrCandidateStatus candidateStatus;
    final Object lock = new Object();

    InstMgrService(Supplier<PathManager> pathManagerSupplier, Consumer<InstMgrService> consumer) {
        this.pathManagerSupplier = pathManagerSupplier;
        this.candidateStatus = new InstMgrCandidateStatus();
        this.consumer = consumer;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        this.pathManager = pathManagerSupplier.get();
        this.homeDir = Path.of(this.pathManager.getPathEntry("jboss.home.dir").resolvePath());

        // Where we are going to store the prepared installations before applying them
        this.prepareServerPath = Paths.get(pathManager.getPathEntry("jboss.controller.temp.dir").resolvePath())
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        // Where we are going to store the custom patch repositories
        this.customPatchPath = homeDir.resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);

        // Properties file used to send information to the launch scripts
        this.propertiesPath = homeDir.resolve("bin").resolve("installation-manager.properties");

        this.candidateStatus.initialize(propertiesPath, prepareServerPath);
        try {
            if (candidateStatus.getStatus() == InstMgrCandidateStatus.Status.PREPARING) {
                candidateStatus.setFailed();
            }
            // Ensure prepare server parent dir is ready
            Files.createDirectories(prepareServerPath.getParent());
        } catch (IOException e) {
            throw new StartException(e);
        }
        started.set(true);
        this.consumer.accept(this);
    }

    @Override
    public void stop(StopContext context) {
        this.pathManager = null;
        try {
            deleteTempDirs();
        } catch (IOException e) {
            InstMgrLogger.ROOT_LOGGER.error(e);
        }
        started.set(false);
        consumer.accept(null);
    }

    Path resolvePath(String path, String relativeTo) {
        checkStarted();
        String relativeToPath = AbsolutePathService.isAbsoluteUnixOrWindowsPath(path) ? null : relativeTo;
        return Paths.get(PathUtils.canonicalize(pathManager.resolveRelativePathEntry(path, relativeToPath))).normalize();
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
            throw new IllegalStateException();
    }

    Path getHomeDir() throws IllegalStateException {
        checkStarted();
        return homeDir;
    }

    Path getCustomPatchDir() throws IllegalStateException {
        checkStarted();
        return customPatchPath;
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

    void beginCandidateServer() {
        this.candidateStatus.begin();
    }

    void commitCandidateServer(String command) {
        this.candidateStatus.commit(command);
    }

    void resetCandidateStatus() {
        this.candidateStatus.reset();
    }

    InstMgrCandidateStatus.Status getCandidateStatus() throws IOException {
        return this.candidateStatus.getStatus();
    }
}
