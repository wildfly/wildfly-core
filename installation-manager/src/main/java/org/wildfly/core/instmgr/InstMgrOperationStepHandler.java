/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.dmr.ModelNode;
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
     * @param is Previously open InputStream of the file to unzip.
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
                    // if the entry is within the file we just create the folder structure and continue
                    current.toFile().mkdirs();
                } else {
                    // just in case the folders were not within the zip file we do check the folder
                    // structure exists and we create it
                    Path parentFolder = current.getParent();
                    if (!Files.exists(parentFolder)) {
                        parentFolder.toFile().mkdirs();
                    }
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
            List<Path> entries = content
                    .filter(e -> e.toFile().isDirectory() && e.getFileName().toString().equals(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES))
                    .collect(Collectors.toList());
            if (entries.size() != 1) {
                throw InstMgrLogger.ROOT_LOGGER.invalidZipEntry(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES);
            }

            return entries.get(0);
        }
    }

    /**
     * The attached Maven Zip files are processed as streams within the current Operation Context, and for each stream, a repository is created.
     * These repositories will be utilized by the installation manager SPI at a later stage.
     *
     * The streams are consumed in parallel and stored as Zip file under the path specified by the workDir parameter. This Zip file is later
     * unzipped on a subdirectory under this work dir and a repository object is created to point out to the directory that contains all the artifacts
     * sent by the client in this stream.
     *
     * @param context The current OperationContext
     * @param mavenRepoFileIndexes A ModelNode that contains the list of indexes of each stream that corresponds to each Maven
     *        Zip file sent by the client
     * @param workDir The workdir where the files will be stored and unzipped
     * @return A list of Repositories to use by the Installation Manager SPI methods
     * @throws OperationFailedException If the Operation is cancelled
     * @throws ExecutionException If any of the tasks that creates the Zip files sent by the client or unzip them fails.
     */
    protected List<Repository> getRepositoriesFromOperationStreams(OperationContext context, List<ModelNode> mavenRepoFileIndexes, Path workDir)
            throws OperationFailedException, ExecutionException {
        final List<CompletableFuture<Repository>> futureResults = new ArrayList<>();
        final Executor mgmtExecutor = imService.getMgmtExecutor();
        for (ModelNode indexMn : mavenRepoFileIndexes) {
            int index = indexMn.asInt();
            CompletableFuture<Path> futureZip = CompletableFuture
                    .supplyAsync(() -> saveMavenZipRepoStream(context, index, workDir, workDir.getFileName().toString()), mgmtExecutor);
            CompletableFuture<Repository> repository = futureZip
                    .thenCompose(zipPath -> CompletableFuture.supplyAsync(() -> createRepoFromZip(zipPath, index, workDir), mgmtExecutor));
            futureResults.add(repository);
        }
        List<Repository> result = new ArrayList<>();
        try {
            for (CompletableFuture<Repository> future : futureResults) {
                result.add(future.get());
            }

            return result;
        } catch (InterruptedException e) {
            for (CompletableFuture<Repository> future : futureResults) {
                future.cancel(true);
            }
            Thread.currentThread().interrupt();
            throw InstMgrLogger.ROOT_LOGGER.operationCancelled();
        } catch (ExecutionException e) {
            for (CompletableFuture<Repository> future : futureResults) {
                future.cancel(true);
            }
            throw e;
        }
    }

    /**
     * Creates a Zip file from a stream attached to the current Operation Context.
     *
     * @param context The Operation Context
     * @param index The index of the stream
     * @param baseWorkDir The base work dir path where this zip file will be created.
     * @param tempFilePrefix The prefix string to be used in generating the file's name.
     *
     * @return The absolute path of the Zip file.
     *
     * @throws RuntimeException If an error occurs.
     */
    private Path saveMavenZipRepoStream(OperationContext context, int index, Path baseWorkDir, String tempFilePrefix) throws RuntimeException {
        try {
            InstMgrLogger.ROOT_LOGGER.debug("Storing as Zip file attachment with index=" + index);
            try (InputStream is = context.getAttachmentStream(index)) {
                Path tempFile = Files.createTempFile(baseWorkDir, tempFilePrefix, ".zip");
                FileOutputStream outputStream = new FileOutputStream(tempFile.toFile());
                byte[] buffer = new byte[1024];

                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                return tempFile;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a Repository to be used with the Installation Manager SPI using a Zip file as a repository source.
     *
     * @param sourceFile Path of the Zip file that contains the maven repository we want to use as a source.
     * @param index The stream index used to get the Zip file.
     * @param baseWorkDir The path of the base work directory
     *
     * @return
     * @throws RuntimeException If an error occurs.
     */
    private Repository createRepoFromZip(Path sourceFile, int index, Path baseWorkDir) throws RuntimeException {
        try {
            InstMgrLogger.ROOT_LOGGER.debug("Unzipping Zip file stored at " + sourceFile + " which was uploaded from index " + index);
            Path repoIdPath = baseWorkDir.resolve(InstMgrConstants.INTERNAL_REPO_PREFIX + index);
            Files.createDirectory(repoIdPath);
            try (FileInputStream fileIs = new FileInputStream(sourceFile.toFile())) {
                unzip(fileIs, repoIdPath);
            }
            Path uploadedMvnRepoRoot = getUploadedMvnRepoRoot(repoIdPath);
            Repository uploadedMavenRepo = new Repository(repoIdPath.getFileName().toString(), uploadedMvnRepoRoot.toUri().toURL().toExternalForm());
            InstMgrLogger.ROOT_LOGGER.debug("Zip file stored at " + sourceFile + " which was uploaded from index " + index + " was unzipped at " + repoIdPath);
            return uploadedMavenRepo;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
