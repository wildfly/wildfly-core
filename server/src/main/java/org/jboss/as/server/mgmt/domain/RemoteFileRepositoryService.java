/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.mgmt.domain;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.as.repository.ContentFilter;

import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.ContentRepositoryElement;
import org.jboss.as.repository.DeploymentFileRepository;
import org.jboss.as.repository.ExplodedContent;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.as.repository.LocalDeploymentFileRepository;
import org.jboss.as.repository.TypedInputStream;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.vfs.VirtualFile;

/**
 * @author Emanuel Muckenhuber
 */
public class RemoteFileRepositoryService implements CompositeContentRepository, Service<ContentRepository> {

    private final InjectedValue<HostControllerClient> clientInjectedValue = new InjectedValue<HostControllerClient>();

    private final File localDeploymentFolder;
    private final DeploymentFileRepository localRepository;
    private final ContentRepository contentRepository;
    private volatile RemoteFileRepositoryExecutor remoteFileRepositoryExecutor;

    public static void addService(final ServiceTarget target, final File localDeploymentContentsFolder, final File localTmpFolder) {
        final RemoteFileRepositoryService service = new RemoteFileRepositoryService(localDeploymentContentsFolder, localTmpFolder);
        target.addService(ContentRepository.SERVICE_NAME, service)
                .addDependency(HostControllerConnectionService.SERVICE_NAME, HostControllerClient.class, service.clientInjectedValue)
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();
    }

    RemoteFileRepositoryService(final File localDeploymentFolder, final File localTmpFolder) {
        this.localDeploymentFolder = localDeploymentFolder;
        this.contentRepository = ContentRepository.Factory.create(localDeploymentFolder, localTmpFolder);
        this.localRepository = new LocalDeploymentFileRepository(localDeploymentFolder);
    }

    @Override
    public void start(final StartContext context) throws StartException {
        final HostControllerClient client = clientInjectedValue.getValue();
        this.remoteFileRepositoryExecutor = client.getRemoteFileRepository();
        contentRepository.readWrite();
    }

    @Override
    public void stop(StopContext context) {
        remoteFileRepositoryExecutor = null;
        contentRepository.readOnly();
    }

    @Override
    public ContentRepository getValue() throws IllegalStateException, IllegalArgumentException {
        final RemoteFileRepositoryExecutor executor = this.remoteFileRepositoryExecutor;
        if (executor == null) {
            throw ServerLogger.ROOT_LOGGER.couldNotFindHcFileRepositoryConnection();
        }
        return this;
    }

    @Override
    public byte[] addContent(InputStream stream) throws IOException {
        return contentRepository.addContent(stream);
    }

    @Override
    public VirtualFile getContent(byte[] hash) {
        return contentRepository.getContent(hash);
    }

    @Override
    public boolean syncContent(ContentReference reference) {
        if (!contentRepository.hasContent(reference.getHash())) {
            getDeploymentFiles(reference); // Make sure it's in sync
        }
        return contentRepository.hasContent(reference.getHash());
    }

    @Override
    public boolean hasContent(byte[] hash) {
        return contentRepository.hasContent(hash);
    }

    @Override
    public void removeContent(ContentReference reference) {
        contentRepository.removeContent(reference);
    }

    @Override
    public final File[] getDeploymentFiles(ContentReference reference) {
        final File root = getDeploymentRoot(reference);
        return root.listFiles();
    }

    @Override
    public File getDeploymentRoot(ContentReference reference) {
        final File file = localRepository.getDeploymentRoot(reference);
        if (!file.exists()) {
            return getFile(reference, DomainServerProtocol.PARAM_ROOT_ID_DEPLOYMENT);
        }
        return file;
    }

    private File getFile(final ContentReference reference, final byte repoId) {
        final RemoteFileRepositoryExecutor executor = this.remoteFileRepositoryExecutor;
        if (executor == null) {
            throw ServerLogger.ROOT_LOGGER.couldNotFindHcFileRepositoryConnection();
        }
        File file = remoteFileRepositoryExecutor.getFile(reference.getHexHash(), repoId, localDeploymentFolder);
        addContentReference(reference);
        return file;
    }

    @Override
    public void deleteDeployment(ContentReference reference) {
        if (hasContent(reference.getHash())) {//Don't delete referenced content in the back
            removeContent(reference);
        } else {
            localRepository.deleteDeployment(reference);
            removeContent(reference);
        }
    }

    @Override
    public void addContentReference(ContentReference reference) {
        contentRepository.addContentReference(reference);
    }

    @Override
    public Map<String, Set<String>> cleanObsoleteContent() {
        return contentRepository.cleanObsoleteContent();
    }

    @Override
    public byte[] removeContentFromExploded(byte[] deploymentHash, List<String> paths) throws ExplodedContentException {
        return contentRepository.removeContentFromExploded(deploymentHash, paths);
    }

    @Override
    public byte[] addContentToExploded(byte[] deploymentHash, List<ExplodedContent> addFiles, boolean overwrite) throws ExplodedContentException {
        return contentRepository.addContentToExploded(deploymentHash, addFiles, overwrite);
    }

    @Override
    public void copyExplodedContent(byte[] hash, Path target) throws ExplodedContentException {
        contentRepository.copyExplodedContent(hash, target);
    }

    @Override
    public void copyExplodedContentFiles(byte[] deploymentHash, List<String> relativePaths, Path target) throws ExplodedContentException {
        contentRepository.copyExplodedContentFiles(deploymentHash, relativePaths, target);
    }

    @Override
    public byte[] explodeContent(byte[] hash) throws ExplodedContentException {
        return contentRepository.explodeContent(hash);
    }

    @Override
    public TypedInputStream readContent(byte[] deploymentHash, String path) throws ExplodedContentException {
        return contentRepository.readContent(deploymentHash, path);
    }

    @Override
    public byte[] explodeSubContent(byte[] deploymentHash, String relativePath) throws ExplodedContentException {
        return contentRepository.explodeSubContent(deploymentHash, relativePath);
    }

    @Override
    public List<ContentRepositoryElement> listContent(byte[] deploymentHash, String path, ContentFilter filter) throws ExplodedContentException {
        return contentRepository.listContent(deploymentHash, path, filter);
    }
}
