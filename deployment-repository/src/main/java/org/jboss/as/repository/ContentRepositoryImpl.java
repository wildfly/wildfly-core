/*
 * Copyright (C) 2016 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.repository;

import static org.jboss.as.repository.ContentRepository.DELETED_CONTENT;
import static org.jboss.as.repository.ContentRepository.MARKED_CONTENT;
import static org.jboss.as.repository.PathUtil.copyRecursively;
import static org.jboss.as.repository.PathUtil.createTempDirectory;
import static org.jboss.as.repository.PathUtil.deleteRecursively;
import static org.jboss.as.repository.PathUtil.deleteSilentlyRecursively;
import static org.jboss.as.repository.PathUtil.getFileExtension;
import static org.jboss.as.repository.PathUtil.isArchive;
import static org.jboss.as.repository.PathUtil.resolveSecurely;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.as.repository.logging.DeploymentRepositoryLogger;
import org.jboss.msc.service.Service;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

import static org.jboss.as.repository.PathUtil.unzip;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.Assert;

/**
 * Default implementation of {@link ContentRepository}.
 *
 * @author John Bailey
 */
public class ContentRepositoryImpl implements ContentRepository, Service<ContentRepository> {

    protected static final String CONTENT = "content";
    private final File repoRoot;
    private final File tmpRoot;
    protected final MessageDigest messageDigest;
    private final Map<String, Set<ContentReference>> contentHashReferences = new HashMap<>();
    private final Map<String, ReentrantLock> lockedContents = new HashMap<>();
    private final Map<String, Long> obsoleteContents = new HashMap<>();
    private final long obsolescenceTimeout;
    private final long lockTimeout;

    protected ContentRepositoryImpl(final File repoRoot, final File tmpRoot, long obsolescenceTimeout, long lockTimeout) {
        Assert.checkNotNullParam("repoRoot", repoRoot);
        Assert.checkNotNullParam("tmpRoot", tmpRoot);
        checkDirectory(repoRoot);
        this.repoRoot = repoRoot;
        checkDirectory(tmpRoot);
        this.tmpRoot = tmpRoot;
        this.obsolescenceTimeout = obsolescenceTimeout;
        this.lockTimeout = lockTimeout;
        try {
            this.messageDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw DeploymentRepositoryLogger.ROOT_LOGGER.cannotObtainSha1(e, MessageDigest.class.getSimpleName());
        }
    }

    private void checkDirectory(final File directory) {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.notADirectory(directory.getAbsolutePath());
            } if (!directory.canWrite()) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.directoryNotWritable(directory.getAbsolutePath());
            }
        } else if (!directory.mkdirs()) {
            throw DeploymentRepositoryLogger.ROOT_LOGGER.cannotCreateDirectory(null, directory.getAbsolutePath());
        }
    }

    @Override
    public byte[] addContent(InputStream stream) throws IOException {
        byte[] sha1Bytes;
        Path tmp = File.createTempFile(CONTENT, ".tmp", repoRoot).toPath();
        if (stream != null) {
            try (OutputStream fos = Files.newOutputStream(tmp)) {
                synchronized (messageDigest) {
                    messageDigest.reset();
                    DigestOutputStream dos = new DigestOutputStream(fos, messageDigest);
                    BufferedInputStream bis = new BufferedInputStream(stream);
                    byte[] bytes = new byte[8192];
                    int read;
                    while ((read = bis.read(bytes)) > -1) {
                        dos.write(bytes, 0, read);
                    }
                    fos.flush();
                }
                sha1Bytes = messageDigest.digest();
            }
        } else {//create a directory instead
            Files.delete(tmp);
            Files.createDirectory(tmp);
            synchronized (messageDigest) {
                messageDigest.reset();
                sha1Bytes = HashUtil.hashPath(messageDigest, tmp);
            }
        }
        final Path realFile = getDeploymentContentFile(sha1Bytes, true);
        if (hasContent(sha1Bytes)) {
            // we've already got this content
            try {
                deleteRecursively(tmp);
            } catch (IOException ioex) {
                DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteTempFile(ioex, tmp.toAbsolutePath().toString());
                tmp.toFile().deleteOnExit();
            }
            DeploymentRepositoryLogger.ROOT_LOGGER.debugf("Content was already present in repository at location %s", realFile.toAbsolutePath().toString());
        } else {
            moveTempToPermanent(tmp, realFile);
            DeploymentRepositoryLogger.ROOT_LOGGER.contentAdded(realFile.toAbsolutePath().toString());
        }
        return sha1Bytes;
    }

    @Override
    public void addContentReference(ContentReference reference) {
        synchronized (contentHashReferences) {
            Set<ContentReference> references = contentHashReferences.get(reference.getHexHash());
            if (references == null) {
                references = new HashSet<>();
                contentHashReferences.put(reference.getHexHash(), references);
            }
            references.add(reference);
        }
    }

    @Override
    public VirtualFile getContent(byte[] hash) {
        Assert.checkNotNullParam("hash", hash);
        return VFS.getChild(getDeploymentContentFile(hash, true).toUri());
    }

    @Override
    public boolean syncContent(ContentReference reference) {
        return hasContent(reference.getHash());
    }

    @Override
    public boolean hasContent(byte[] hash) {
        return Files.exists(getDeploymentContentFile(hash));
    }

    protected Path getRepoRoot() {
        return repoRoot.toPath();
    }

    protected Path getDeploymentContentFile(byte[] deploymentHash) {
        return getDeploymentContentFile(deploymentHash, false);
    }

    private Path getDeploymentContentFile(byte[] deploymentHash, boolean validate) {
        return getDeploymentHashDir(deploymentHash, validate).resolve(CONTENT);
    }

    protected Path getDeploymentHashDir(final byte[] deploymentHash, final boolean validate) {
        final String sha1 = HashUtil.bytesToHexString(deploymentHash);
        final String partA = sha1.substring(0, 2);
        final String partB = sha1.substring(2);
        final Path base = getRepoRoot().resolve(partA);
        if (validate) {
            validateDir(base);
        }
        final Path hashDir = base.resolve(partB);
        if (validate && !Files.exists(hashDir)) {
            try {
                Files.createDirectories(hashDir);
            } catch (IOException ioex) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.cannotCreateDirectory(ioex, hashDir.toAbsolutePath().toString());
            }
        }
        return hashDir;
    }

    protected void validateDir(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException ioex) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.cannotCreateDirectory(ioex, dir.toAbsolutePath().toString());
            }
        } else if (!Files.isDirectory(dir)) {
            throw DeploymentRepositoryLogger.ROOT_LOGGER.notADirectory(dir.toAbsolutePath().toString());
        } else if (!dir.toFile().canWrite()) { //WFCORE-799 workaround for broken Files.isWritable() on Windows in JDK8
            throw DeploymentRepositoryLogger.ROOT_LOGGER.directoryNotWritable(dir.toAbsolutePath().toString());
        }
    }

    private void moveTempToPermanent(Path tmpFile, Path permanentFile) throws IOException {
        Path localTmp = permanentFile.resolveSibling("tmp");
        try {
            Files.move(tmpFile, permanentFile);
        } catch (IOException ioex) {
            // AS7-3574. Try to avoid writing the permanent file bit by bit in we crash in the middle.
            // Copy tmpFile to another tmpfile in the same dir as the permanent file (and thus same filesystem)
            // and see then if we can rename it.
            copyRecursively(tmpFile, localTmp, true);
            try {
                Files.move(localTmp, permanentFile);
            } catch (IOException ex) {
                // No luck; need to copy
                try {
                    copyRecursively(localTmp, permanentFile, true);
                } catch (IOException e) {
                    deleteRecursively(permanentFile);
                    throw e;
                }
            }
        } finally {
            try {
                deleteRecursively(tmpFile);
            } catch (IOException ioex) {
                DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteTempFile(ioex, tmpFile.toString());
                tmpFile.toFile().deleteOnExit();
            }
            try {
                deleteRecursively(localTmp);
            } catch (IOException ioex) {
                DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteTempFile(ioex, localTmp.toString());
                localTmp.toFile().deleteOnExit();
            }
        }
    }

    @Override
    public void removeContent(ContentReference reference) {
        synchronized (contentHashReferences) {
            final Set<ContentReference> references = contentHashReferences.get(reference.getHexHash());
            if (references != null) {
                references.remove(reference);
                if (!references.isEmpty()) {
                    return;
                }
                contentHashReferences.remove(reference.getHexHash());
            }
        }
        Path contentPath;
        if (!HashUtil.isEachHexHashInTable(reference.getHexHash())) {
            contentPath = Paths.get(reference.getContentIdentifier());
        } else {
            contentPath = getDeploymentContentFile(reference.getHash(), false);
        }
        try {
            if (HashUtil.isEachHexHashInTable(reference.getHexHash())) { //Otherwise this is not a deployment content
                if(!lock(reference.getHash())) {
                    DeploymentRepositoryLogger.ROOT_LOGGER.contentDeletionError(DeploymentRepositoryLogger.ROOT_LOGGER.errorLockingDeployment(), contentPath.toString());
                    return;
                }
            }
            deleteRecursively(contentPath);
        } catch (IOException ex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.contentDeletionError(ex, contentPath.toString());
        } catch (InterruptedException ex) {
            Thread.interrupted();
            DeploymentRepositoryLogger.ROOT_LOGGER.contentDeletionError(ex, contentPath.toString());
        } finally {
            if (HashUtil.isEachHexHashInTable(reference.getHexHash())) {
                unlock(reference.getHash());
            }
        }
        Path parent = contentPath.getParent();
        try {
            Files.deleteIfExists(parent);
        } catch (IOException ex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.contentDeletionError(ex, parent.toString());
        }
        Path grandParent = parent.getParent();
        if(Files.exists(grandParent)) {
            try (Stream<Path> files = Files.list(grandParent)) {
                if (!files.findAny().isPresent()) {
                    Files.deleteIfExists(grandParent);
                }
            } catch (IOException ex) {
                DeploymentRepositoryLogger.ROOT_LOGGER.contentDeletionError(ex, grandParent.toString());
            }
        }
        DeploymentRepositoryLogger.ROOT_LOGGER.contentRemoved(contentPath.toAbsolutePath().toString());
    }

    /**
     * Clean obsolete contents from the content repository. It will first mark contents as obsolete then after some time
     * if these contents are still obsolete they will be removed.
     *
     * @return a map containing the list of marked contents and the list of deleted contents.
     */
    @Override
    public Map<String, Set<String>> cleanObsoleteContent() {
        Map<String, Set<String>> cleanedContents = new HashMap<>(2);
        cleanedContents.put(MARKED_CONTENT, new HashSet<>());
        cleanedContents.put(DELETED_CONTENT, new HashSet<>());
        synchronized (contentHashReferences) {
            for (ContentReference fsContent : listLocalContents()) {
                if (!contentHashReferences.containsKey(fsContent.getHexHash())) { //We have no refrence to this content
                    if (markAsObsolete(fsContent)) {
                        cleanedContents.get(DELETED_CONTENT).add(fsContent.getContentIdentifier());
                    } else {
                        cleanedContents.get(MARKED_CONTENT).add(fsContent.getContentIdentifier());
                    }
                } else {
                    obsoleteContents.remove(fsContent.getHexHash()); //Remove existing references from obsoleteContents
                }
            }
        }
        return cleanedContents;
    }

    /**
     * Mark content as obsolete. If content was already marked for obsolescenceTimeout ms then it is removed.
     *
     * @param ref the content refrence to be marked as obsolete.
     *
     * @return true if the content refrence is removed, fale otherwise.
     */
    private boolean markAsObsolete(ContentReference ref) {
        if (obsoleteContents.containsKey(ref.getHexHash())) { //This content is already marked as obsolete
            if (obsoleteContents.get(ref.getHexHash()) + obsolescenceTimeout < System.currentTimeMillis()) {
                DeploymentRepositoryLogger.ROOT_LOGGER.obsoleteContentCleaned(ref.getContentIdentifier());
                removeContent(ref);
                return true;
            }
        } else {
            obsoleteContents.put(ref.getHexHash(), System.currentTimeMillis()); //Mark content as obsolete
        }
        return false;
    }

    private Set<ContentReference> listLocalContents() {
        Set<ContentReference> localReferences = new HashSet<>();
        File[] rootHashes = repoRoot.listFiles();
        if (rootHashes != null) {
            for (File rootHash : rootHashes) {
                if (rootHash.isDirectory()) {
                    File[] complementaryHashes = rootHash.listFiles();
                    if (complementaryHashes == null || complementaryHashes.length == 0) {
                        ContentReference reference = new ContentReference(rootHash.getAbsolutePath(), rootHash.getName());
                        localReferences.add(reference);
                    } else {
                        for (File complementaryHash : complementaryHashes) {
                            String hash = rootHash.getName() + complementaryHash.getName();
                            ContentReference reference = new ContentReference(complementaryHash.getAbsolutePath(), hash);
                            localReferences.add(reference);
                        }
                    }
                }
            }
        } else {
            DeploymentRepositoryLogger.ROOT_LOGGER.localContentListError(repoRoot.getAbsolutePath());
        }
        return localReferences;
    }

    @Override
    public byte[] explodeContent(byte[] deploymentHash) throws ExplodedContentException {
        Path contentPath = getDeploymentContentFile(deploymentHash);
        if (!Files.exists(contentPath)) {
            throw DeploymentRepositoryLogger.ROOT_LOGGER.archiveNotFound(contentPath.toString());
        }
        try {
            if (Files.isDirectory(contentPath) || !isArchive(contentPath)) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.notAnArchive(contentPath.toString());
            }
            Path tmp = createTempDirectory(repoRoot.toPath(), CONTENT);
            Path contentDir = Files.createDirectory(tmp.resolve(CONTENT));
            unzip(contentPath, contentDir);
            byte[] sha1Bytes = HashUtil.hashPath(messageDigest, contentDir);
            final Path realFile = getDeploymentContentFile(sha1Bytes, true);
            if (hasContent(sha1Bytes)) {
                // we've already got this content
                try {
                    deleteRecursively(tmp);
                } catch (IOException ioex) {
                    DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteTempFile(ioex, tmp.toAbsolutePath().toString());
                    tmp.toFile().deleteOnExit();
                }
                DeploymentRepositoryLogger.ROOT_LOGGER.debugf("Content was already present in repository at location %s", realFile.toAbsolutePath().toString());
            } else {
                moveTempToPermanent(contentDir, realFile);
                deleteRecursively(tmp);
                DeploymentRepositoryLogger.ROOT_LOGGER.contentExploded(realFile.toAbsolutePath().toString());
            }
            return sha1Bytes;
        } catch (IOException ioex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.warn(ioex);
            throw DeploymentRepositoryLogger.ROOT_LOGGER.errorExplodingContent(ioex, contentPath.toString());
        }
    }

    @Override
    public byte[] explodeSubContent(byte[] deploymentHash, String relativePath) throws ExplodedContentException {
        Path contentPath = getDeploymentContentFile(deploymentHash);
        Path sourcePath = resolveSecurely(contentPath, relativePath);
        try {
            if (Files.exists(contentPath) && Files.isDirectory(contentPath)) {
                Path tmp = createTempDirectory(repoRoot.toPath(), CONTENT);
                Path contentDir = tmp.resolve(CONTENT);
                copyRecursively(contentPath, contentDir, true);
                Path targetPath = resolveSecurely(contentDir, relativePath);
                if (!Files.exists(sourcePath)) {
                    throw DeploymentRepositoryLogger.ROOT_LOGGER.archiveNotFound(sourcePath.toString());
                }
                if (Files.isDirectory(sourcePath) || !isArchive(sourcePath)) {
                    throw DeploymentRepositoryLogger.ROOT_LOGGER.notAnArchive(sourcePath.toString());
                }
                if (Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
                    deleteRecursively(targetPath);
                }
                unzip(sourcePath, targetPath);
                byte[] sha1Bytes = HashUtil.hashPath(messageDigest, contentDir);
                final Path realFile = getDeploymentContentFile(sha1Bytes, true);
                if (hasContent(sha1Bytes)) {
                    // we've already got this content
                    try {
                        deleteRecursively(tmp);
                    } catch (IOException ioex) {
                        DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteTempFile(ioex, tmp.toAbsolutePath().toString());
                        tmp.toFile().deleteOnExit();
                    }
                    DeploymentRepositoryLogger.ROOT_LOGGER.debugf("Content was already present in repository at location %s", realFile.toAbsolutePath().toString());
                } else {
                    moveTempToPermanent(contentDir, realFile);
                    deleteRecursively(tmp);
                    DeploymentRepositoryLogger.ROOT_LOGGER.contentAdded(realFile.toAbsolutePath().toString());
                }
                return sha1Bytes;
            }
            return deploymentHash;
        } catch (IOException ioex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.warn(ioex);
            throw DeploymentRepositoryLogger.ROOT_LOGGER.errorExplodingContent(ioex, sourcePath.toString());
        }
    }

    @Override
    public void copyExplodedContent(byte[] deploymentHash, final Path target) throws ExplodedContentException {
        final Path contentPath = getDeploymentContentFile(deploymentHash);
        try {
            if (Files.exists(contentPath) && Files.isDirectory(contentPath)) {
                copyRecursively(contentPath, target, false);
            }
        } catch (IOException ioex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.warn(ioex);
            throw DeploymentRepositoryLogger.ROOT_LOGGER.errorCopyingDeployment(ioex, target.toString());
        }
    }

    @Override
    public void copyExplodedContentFiles(byte[] deploymentHash, List<String> relativePaths, Path target) throws ExplodedContentException {
        final Path contentPath = getDeploymentContentFile(deploymentHash);
        try {
            if (Files.exists(contentPath) && Files.isDirectory(contentPath)) {
                for (String relativePath : relativePaths) {
                    copyRecursively(resolveSecurely(contentPath, relativePath), resolveSecurely(target, relativePath), true);
                }
            }
        } catch (IOException ioex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.warn(ioex);
            throw DeploymentRepositoryLogger.ROOT_LOGGER.errorCopyingDeployment(ioex, target.toString());
        }
    }

    private boolean lock(byte[] hash) throws InterruptedException {
        String hashHex = HashUtil.bytesToHexString(hash);
        synchronized(lockedContents) {
            if(!lockedContents.containsKey(hashHex)) {
                lockedContents.put(hashHex, new ReentrantLock());
            }
            return lockedContents.get(hashHex).tryLock(lockTimeout, TimeUnit.MILLISECONDS);
        }
    }

    private void unlock(byte[] hash) {
        String hashHex = HashUtil.bytesToHexString(hash);
        synchronized (lockedContents) {
            if (lockedContents.containsKey(hashHex)) {
                ReentrantLock lock = lockedContents.get(hashHex);
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    if (!Files.exists(getDeploymentContentFile(hash))) {
                        lockedContents.remove(hashHex);
                    }
                }
            }
        }
    }

    @Override
    public TypedInputStream readContent(byte[] deploymentHash, String path) throws ExplodedContentException {
        Path tmpDir = null;
        try {
            if(!lock(deploymentHash)) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.errorLockingDeployment();
            }
            Path src = resolveSecurely(getDeploymentContentFile(deploymentHash), path);
            tmpDir = Files.createTempDirectory(tmpRoot.toPath(), HashUtil.bytesToHexString(deploymentHash));
            Path file = PathUtil.readFile(src, tmpDir);
            Path tmp = Files.createTempFile(tmpRoot.toPath(), CONTENT, getFileExtension(src));
            Files.copy(file, tmp, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            return new TemporaryFileInputStream(tmp);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.warn(ex);
            throw DeploymentRepositoryLogger.ROOT_LOGGER.errorAccessingDeployment(ex);
        } finally {
            unlock(deploymentHash);
            deleteSilentlyRecursively(tmpDir);
        }
    }

    @Override
    public List<ContentRepositoryElement> listContent(byte[] deploymentHash, String path, ContentFilter filter) throws ExplodedContentException {
        Path tmpDir = null;
        try {
            if (!lock(deploymentHash)) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.errorLockingDeployment();
            }
            tmpDir = Files.createTempDirectory(tmpRoot.toPath(), HashUtil.bytesToHexString(deploymentHash));
            final Path rootPath = resolveSecurely(getDeploymentContentFile(deploymentHash), path);
            List<ContentRepositoryElement> result = PathUtil.listFiles(rootPath, tmpDir, filter);
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.warn(ex);
            throw DeploymentRepositoryLogger.ROOT_LOGGER.errorAccessingDeployment(ex);
        } finally {
            unlock(deploymentHash);
            if(tmpDir != null) {
                deleteSilentlyRecursively(tmpDir);
            }
        }
    }

    private void deleteFileWithEmptyAncestorDirectories(Path path) throws IOException {
        if (!this.repoRoot.toPath().equals(path)) {
            Files.deleteIfExists(path);
            Path parent = path.getParent();
            try (Stream<Path> files = Files.list(parent)) {
                if (Files.isDirectory(parent) && !files.findAny().isPresent()) {
                    deleteFileWithEmptyAncestorDirectories(parent);
                }
            }
        }
    }

    @Override
    public byte[] addContentToExploded(byte[] deploymentHash, List<ExplodedContent> addFiles, boolean overwrite) throws ExplodedContentException {
        Path contentPath = getDeploymentContentFile(deploymentHash);
        try {
            if (Files.exists(contentPath) && Files.isDirectory(contentPath)) {
                Path tmp = createTempDirectory(repoRoot.toPath(), CONTENT);
                Path contentDir = tmp.resolve(CONTENT);
                copyRecursively(contentPath, contentDir, overwrite);
                for (ExplodedContent newContent : addFiles) {
                    Path targetFile = resolveSecurely(contentDir, newContent.getRelativePath());
                    if (!Files.exists(targetFile)) {
                        Files.createDirectories(targetFile.getParent());
                    }
                    try (InputStream in = newContent.getContent()) {
                        if(in == null) {
                            Files.createDirectory(targetFile);
                        } else {
                            if(overwrite) {
                                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                            } else {
                                Files.copy(in, targetFile);
                            }
                        }
                    }
                }
                byte[] sha1Bytes = HashUtil.hashPath(messageDigest, contentDir);
                final Path realFile = getDeploymentContentFile(sha1Bytes, true);
                if (hasContent(sha1Bytes)) {
                    // we've already got this content
                    try {
                        deleteRecursively(tmp);
                    } catch (IOException ioex) {
                        DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteTempFile(ioex, tmp.toAbsolutePath().toString());
                        tmp.toFile().deleteOnExit();
                    }
                    DeploymentRepositoryLogger.ROOT_LOGGER.debugf("Content was already present in repository at location %s", realFile.toAbsolutePath().toString());
                } else {
                    moveTempToPermanent(contentDir, realFile);
                    deleteRecursively(tmp);
                    DeploymentRepositoryLogger.ROOT_LOGGER.contentAdded(realFile.toAbsolutePath().toString());
                }
                return sha1Bytes;
            }
            return deploymentHash;
        } catch (IOException ex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.warn(ex);
            throw DeploymentRepositoryLogger.ROOT_LOGGER.errorUpdatingDeployment(ex);
        }
    }

    @Override
    public byte[] removeContentFromExploded(byte[] deploymentHash, List<String> paths) throws ExplodedContentException {
        Path contentPath = getDeploymentContentFile(deploymentHash);
        try {
            if (Files.exists(contentPath) && Files.isDirectory(contentPath)) {
                Path tmp = createTempDirectory(repoRoot.toPath(), CONTENT);
                Path contentDir = tmp.resolve(CONTENT).toAbsolutePath();
                copyRecursively(contentPath, contentDir, false);
                for (String path : paths) {
                    Path targetFile = resolveSecurely(contentDir, path);
                    deleteFileWithEmptyAncestorDirectories(targetFile);
                }
                byte[] sha1Bytes = HashUtil.hashPath(messageDigest, contentDir);
                final Path realFile = getDeploymentContentFile(sha1Bytes, true);
                if (hasContent(sha1Bytes)) {
                    // we've already got this content
                    try {
                        deleteRecursively(tmp);
                    } catch (IOException ioex) {
                        DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteTempFile(ioex, tmp.toAbsolutePath().toString());
                        tmp.toFile().deleteOnExit();
                    }
                    DeploymentRepositoryLogger.ROOT_LOGGER.debugf("Content was already present in repository at location %s", realFile.toAbsolutePath().toString());
                } else {
                    moveTempToPermanent(contentDir, realFile);
                    deleteRecursively(tmp);
                    DeploymentRepositoryLogger.ROOT_LOGGER.contentAdded(realFile.toAbsolutePath().toString());
                }
                return sha1Bytes;
            }
            return deploymentHash;
        } catch (IOException ex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.warn(ex);
            throw DeploymentRepositoryLogger.ROOT_LOGGER.errorUpdatingDeployment(ex);
        }
    }

    @Override
    public void start(StartContext context) throws StartException {
        DeploymentRepositoryLogger.ROOT_LOGGER.debugf("%s started", ContentRepository.class.getSimpleName());
    }

    @Override
    public void stop(StopContext context) {
        DeploymentRepositoryLogger.ROOT_LOGGER.debugf("%s stopped", ContentRepository.class.getSimpleName());
    }

    @Override
    public ContentRepository getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
