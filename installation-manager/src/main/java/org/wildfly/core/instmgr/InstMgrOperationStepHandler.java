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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Base class for installation-manager operation handlers.
 */
abstract class InstMgrOperationStepHandler implements OperationStepHandler {
    protected final InstMgrService imService;
    protected final InstallationManagerFactory imf;

    InstMgrOperationStepHandler(InstMgrService imService, InstallationManagerFactory imf) {
        this.imService = imService;
        this.imf = imf;
    }

    protected void deleteDirIfExits(Path dir, boolean skipRootDir) throws IOException {
        if (dir != null && dir.toFile().exists()) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(f -> !skipRootDir || skipRootDir && !f.equals(dir))
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    protected void deleteDirIfExits(Path dir) throws IOException {
        deleteDirIfExits(dir, false);
    }

    /**
     * Unzips a Zip file by using an InputStream to a specific target directory.
     *
     * @param is        Previously open InputStream of the file to unzip.
     * @param targetDir Target Directory where the content will stored.
     *
     * @throws ZipException if a ZIP file error has occurred
     * @throws IOException if an I/O error has occurred
     */
    protected void unzip(InputStream is, final Path targetDir) throws IOException, ZipException {
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                final String name = entry.getName();
                final Path current = targetDir.resolve(name);
                if (!current.normalize().startsWith(targetDir.normalize())) {
                    throw InstMgrLogger.ROOT_LOGGER.zipEntryOutsideOfTarget(current.toRealPath().toString(), targetDir.toRealPath().toString());
                }
                if (entry.isDirectory()) {
                    current.toFile().mkdirs();
                } else {
                    Files.copy(zis, current, StandardCopyOption.REPLACE_EXISTING);
                }
                entry = zis.getNextEntry();
            }
        }
    }

    /**
     * Finds the expected directory where the artifacts in a Maven Zip Archive can be located.
     *
     * @param source Directory to where look for the maven repository.
     * @return
     * @throws ZipException if a ZIP file error has occurred
     * @throws IOException if an I/O error has occurred
     */
    protected Path getUploadedMvnRepoRoot(Path source) throws Exception, ZipException {
        try (Stream<Path> content = Files.walk(source, 2)) {
            List<Path> entries = content.filter(e -> e.toFile().isDirectory() && e.getFileName().toString().equals(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES)).collect(Collectors.toList());
            if (entries.isEmpty() || entries.size() != 1) {
                throw InstMgrLogger.ROOT_LOGGER.invalidZipEntry(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
            }

            return entries.get(0);
        }
    }

    /**
     * Process a Maven Zip file attached as stream to the current Operation Context to create a repository that will be later used by the installation
     * manager SPI.
     * The stream is consumed and saved as a Zip file under the baseWorkDir Path. Then, this file is unzipped on repoIdPath and a repository is created to point
     * out to the directory that contains all the artifacts send by the client in this stream.
     */
    protected Repository processMavenRepoFile(OperationContext context, Path repoIdPath, int streamIndex, Path baseWorkDir, String tempFilePrefix)
            throws Exception {
        // save and unzip the file in the target dir
        // Using directly the Operation input stream unzipping without saving it previously to disk seems to
        // be problematic
        try (InputStream is = context.getAttachmentStream(streamIndex)) {
            Path tempFile = Files.createTempFile(baseWorkDir, tempFilePrefix, ".zip");
            FileOutputStream outputStream = new FileOutputStream(tempFile.toFile());
            byte[] buffer = new byte[1024];

            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            Files.createDirectory(repoIdPath);
            try (FileInputStream fileIs = new FileInputStream(tempFile.toFile())) {
                unzip(fileIs, repoIdPath);
            }
        }
        Path uploadedMvnRepoRoot = getUploadedMvnRepoRoot(repoIdPath);
        Repository uploadedMavenRepo = new Repository(repoIdPath.getFileName().toString(), uploadedMvnRepoRoot.toUri().toString());
        return uploadedMavenRepo;
    }
}
