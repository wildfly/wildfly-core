/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.repository;

import static java.lang.Long.getLong;
import static java.lang.System.getSecurityManager;
import static java.security.AccessController.doPrivileged;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.jboss.as.repository.logging.DeploymentRepositoryLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

/**
 * Repository for deployment content and other managed content.
 *
 * @author John Bailey
 */
public interface ContentRepository {

    /**
     * Standard ServiceName under which a service controller for an instance of
     * @code Service<ContentRepository> would be registered.
     */
    ServiceName SERVICE_NAME = ServiceName.JBOSS.append("content-repository");

    /**
     * Time after which a marked obsolete content will be removed.
     * Currently 5 minutes.
     */
    long OBSOLETE_CONTENT_TIMEOUT = getSecurityManager() == null ? getLong(Factory.UNSUPPORTED_PROPERTY, 300000L) : doPrivileged((PrivilegedAction<Long>) () -> getLong(Factory.UNSUPPORTED_PROPERTY, 300000L));

    String DELETED_CONTENT = "deleted-contents";
    String MARKED_CONTENT = "marked-contents";

    /**
     * Add the given content to the repository along with a reference tracked by {@code name}.
     *
     * @param stream stream from which the content can be read. Cannot be <code>null</code>
     * @return the hash of the content that will be used as an internal identifier for the content. Will not be
     * <code>null</code>
     * @throws IOException if there is a problem reading the stream
     */
    byte[] addContent(InputStream stream) throws IOException;

    /**
     * Adds a reference to the content.
     *
     * @param reference a reference to the content to be referenced. This is also used in
     * {@link #removeContent(ContentReference reference)}
     */
    void addContentReference(ContentReference reference);

    /**
     * Get the content as a virtual file.
     *
     * @param hash the hash. Cannot be {@code null}
     *
     * @return the content as a virtual file
     */
    VirtualFile getContent(byte[] hash);

    /**
     * Gets whether content with the given hash is stored in the repository.
     *
     * @param hash the hash. Cannot be {@code null}
     *
     * @return {@code true} if the repository has content with the given hash.
     */
    boolean hasContent(byte[] hash);

    /**
     * Synchronize content with the given reference. This may be used in favor of {@linkplain #hasContent(byte[])} to
     * explicitly allow additional operations to synchronize the local content with some external repository.
     *
     * @param reference the reference to be synchronized. Cannot be {@code null}
     *
     * @return {@code true} if the repository has content with the given reference
     */
    boolean syncContent(ContentReference reference);

    /**
     * Remove the given content from the repository.
     *
     * Remove the given content from the repository. The reference will be removed, and if there are no references left
     * the content will be totally removed.
     *
     * @param reference a reference to the content to be unreferenced. This is also used in
     * {@link #addContentReference(ContentReference reference)}
     */
    void removeContent(ContentReference reference);

    /**
     * Clean content that is not referenced from the repository.
     *
     * Remove the contents that are no longer referenced from the repository.
     *
     * @return the list of obsolete contents that were removed and the list of obsolete contents that were marked to
     * be removed.
     */
    Map<String, Set<String>> cleanObsoleteContent();

    static class Factory {
        /**
         * For testing purpose only.
         * @deprecated DON'T USE IT.
         */
        @Deprecated
        private static final String UNSUPPORTED_PROPERTY = "org.wildfly.unsupported.content.repository.obsolescence";
        public static void addService(final ServiceTarget serviceTarget, final File repoRoot) {
            ContentRepositoryImpl contentRepository = new ContentRepositoryImpl(repoRoot, OBSOLETE_CONTENT_TIMEOUT);
            serviceTarget.addService(SERVICE_NAME, contentRepository).install();
        }

        public static ContentRepository create(final File repoRoot) {
            return create(repoRoot, OBSOLETE_CONTENT_TIMEOUT);
        }

        static ContentRepository create(final File repoRoot, long timeout) {
            return new ContentRepositoryImpl(repoRoot, timeout);
        }

        /**
         * Default implementation of {@link ContentRepository}.
         *
         * @author John Bailey
         */
        private static class ContentRepositoryImpl implements ContentRepository, Service<ContentRepository> {

            protected static final String CONTENT = "content";
            private final File repoRoot;
            protected final MessageDigest messageDigest;
            private final Map<String, Set<ContentReference>> contentHashReferences = new HashMap<String, Set<ContentReference>>();
            private final Map<String, Long> obsoleteContents = new HashMap<String, Long>();
            private final long obsolescenceTimeout;

            protected ContentRepositoryImpl(final File repoRoot, long obsolescenceTimeout) {
                if (repoRoot == null) {
                    throw DeploymentRepositoryLogger.ROOT_LOGGER.nullVar("repoRoot");
                }
                if (repoRoot.exists()) {
                    if (!repoRoot.isDirectory()) {
                        throw DeploymentRepositoryLogger.ROOT_LOGGER.notADirectory(repoRoot.getAbsolutePath());
                    } else if (!repoRoot.canWrite()) {
                        throw DeploymentRepositoryLogger.ROOT_LOGGER.directoryNotWritable(repoRoot.getAbsolutePath());
                    }
                } else if (!repoRoot.mkdirs()) {
                    throw DeploymentRepositoryLogger.ROOT_LOGGER.cannotCreateDirectory(null, repoRoot.getAbsolutePath());
                }
                this.repoRoot = repoRoot;
                this.obsolescenceTimeout = obsolescenceTimeout;
                try {
                    this.messageDigest = MessageDigest.getInstance("SHA-1");
                } catch (NoSuchAlgorithmException e) {
                    throw DeploymentRepositoryLogger.ROOT_LOGGER.cannotObtainSha1(e, MessageDigest.class.getSimpleName());
                }
            }

            @Override
            public byte[] addContent(InputStream stream) throws IOException {
                byte[] sha1Bytes;
                Path tmp = File.createTempFile(CONTENT, ".tmp", repoRoot).toPath();
                OutputStream fos = Files.newOutputStream(tmp);
                synchronized (messageDigest) {
                    messageDigest.reset();
                    try {
                        DigestOutputStream dos = new DigestOutputStream(fos, messageDigest);
                        BufferedInputStream bis = new BufferedInputStream(stream);
                        byte[] bytes = new byte[8192];
                        int read;
                        while ((read = bis.read(bytes)) > -1) {
                            dos.write(bytes, 0, read);
                        }
                        fos.flush();
                        fos.close();
                        fos = null;
                    } finally {
                        safeClose(fos);
                    }
                    sha1Bytes = messageDigest.digest();
                }
                final Path realFile = getDeploymentContentFile(sha1Bytes, true);
                if (hasContent(sha1Bytes)) {
                    // we've already got this content
                    try {
                        Files.deleteIfExists(tmp);
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
                        references = new HashSet<ContentReference>();
                        contentHashReferences.put(reference.getHexHash(), references);
                    }
                    references.add(reference);
                }
            }

            @Override
            public VirtualFile getContent(byte[] hash) {
                if (hash == null) {
                    throw DeploymentRepositoryLogger.ROOT_LOGGER.nullVar("hash");
                }
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
                    Files.copy(tmpFile, localTmp);
                    try {
                        Files.move(localTmp, permanentFile);
                    } catch (IOException ex) {
                        // No luck; need to copy
                        try {
                            Files.copy(localTmp, permanentFile);
                        } catch (IOException e) {
                            Files.deleteIfExists(permanentFile);
                            throw e;
                        }
                    }
                } finally {
                    try {
                        Files.deleteIfExists(tmpFile);
                    } catch (IOException ioex) {
                        DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteTempFile(ioex, tmpFile.toString());
                        tmpFile.toFile().deleteOnExit();
                    }
                    try {
                        Files.deleteIfExists(localTmp);
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

                Path file;
                if (!HashUtil.isEachHexHashInTable(reference.getHexHash())) {
                    String identifier = reference.getContentIdentifier();
                    file = Paths.get(identifier);
                } else
                    file = getDeploymentContentFile(reference.getHash(), true);

                try {
                    Files.deleteIfExists(file);
                } catch (IOException ex) {
                    DeploymentRepositoryLogger.ROOT_LOGGER.contentDeletionError(ex, file.toString());
                }
                Path parent = file.getParent();
                try {
                    Files.deleteIfExists(parent);
                } catch (IOException ex) {
                    DeploymentRepositoryLogger.ROOT_LOGGER.contentDeletionError(ex, parent.toString());
                }
                Path grandParent = parent.getParent();
                try (Stream<Path> files = Files.list(grandParent)){
                    if (!files.findAny().isPresent()) {
                        Files.deleteIfExists(grandParent);
                    }
                } catch (IOException ex) {
                    DeploymentRepositoryLogger.ROOT_LOGGER.contentDeletionError(ex, grandParent.toString());
                }
                DeploymentRepositoryLogger.ROOT_LOGGER.contentRemoved(file.toAbsolutePath().toString());
            }

            /**
             * Clean obsolete contents from the content repository.
             * It will first mark contents as obsolete then after some time if these contents are still obsolete they
             * will be removed.
             *
             * @return a map containing the list of marked contents and the list of deleted contents.
             */
            @Override
            public Map<String, Set<String>> cleanObsoleteContent() {
                Map<String, Set<String>> cleanedContents = new HashMap<String, Set<String>>(2);
                cleanedContents.put(MARKED_CONTENT, new HashSet<String>());
                cleanedContents.put(DELETED_CONTENT, new HashSet<String>());
                synchronized (contentHashReferences) {
                    for (ContentReference fsContent : listLocalContents()) {
                        if (!contentHashReferences.containsKey(fsContent.getHexHash())) { //We have no refrence to this content
                            if(markAsObsolete(fsContent)) {
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

            protected static void safeClose(final Closeable closeable) {
                if (closeable != null) {
                    try {
                        closeable.close();
                    } catch (Exception ignore) {
                        //
                    }
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

    }
}
