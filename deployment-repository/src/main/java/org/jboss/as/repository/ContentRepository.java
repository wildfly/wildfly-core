/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import static java.lang.System.getSecurityManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivilegedAction;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.vfs.VirtualFile;

import static java.lang.Long.getLong;
import static java.security.AccessController.doPrivileged;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /**
     * Timeout duration to get a lock on a deployment for removing it or reading some content.
     * Currently 5 seconds.
     */
    long LOCK_TIMEOUT = 5000L;
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

    default byte[] explodeContent(byte[] deploymentHash) throws ExplodedContentException {
        return deploymentHash;
    }

    default byte[] explodeSubContent(byte[] deploymentHash, String relativePath) throws ExplodedContentException {
        throw new UnsupportedOperationException();
    }

    default void copyExplodedContent(byte[] deploymentHash, Path target) throws ExplodedContentException {
    }

    default void copyExplodedContentFiles(byte[] deploymentHash, List<String> relativePaths, Path target) throws ExplodedContentException {
    }

    default byte[] addContentToExploded(byte[] deploymentHash, List<ExplodedContent> addFiles, boolean overwrite) throws ExplodedContentException {
        throw new UnsupportedOperationException();
    }

    default byte[] removeContentFromExploded(byte[] deploymentHash, List<String> paths) throws ExplodedContentException {
        throw new UnsupportedOperationException();
    }

    default TypedInputStream readContent(byte[] deploymentHash, String path) throws ExplodedContentException {
        throw new UnsupportedOperationException();
    }

    default List<ContentRepositoryElement> listContent(byte[] deploymentHash, String path, ContentFilter filter) throws ExplodedContentException {
        return Collections.emptyList();
    }

    default void readWrite() {
    }

    default void readOnly() {
    }

    default void flush(boolean success) {
    }

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
            addService(serviceTarget, repoRoot, repoRoot);
        }

        public static void addService(final ServiceTarget serviceTarget, final File repoRoot, final File tmpRoot) {
            addService(serviceTarget, new ContentRepositoryImpl(repoRoot, tmpRoot, OBSOLETE_CONTENT_TIMEOUT, LOCK_TIMEOUT));
        }

        public static void addService(final ServiceTarget serviceTarget, final ContentRepository contentRepository) {
            serviceTarget.addService(SERVICE_NAME,new ContentRepositoryService(contentRepository)).install();
        }

        public static ContentRepository create(final File repoRoot) {
            return create(repoRoot, repoRoot, OBSOLETE_CONTENT_TIMEOUT);
        }

        public static ContentRepository create(final File repoRoot, final File tmpRoot) {
            return create(repoRoot, tmpRoot, OBSOLETE_CONTENT_TIMEOUT);
        }

        static ContentRepository create(final File repoRoot, final File tmpRoot, long timeout) {
            return create(repoRoot, tmpRoot, timeout, LOCK_TIMEOUT);
        }

        static ContentRepository create(final File repoRoot, final File tmpRoot, long timeout, long lock) {
            return new ContentRepositoryImpl(repoRoot, tmpRoot, timeout, lock);
        }
    }
}
