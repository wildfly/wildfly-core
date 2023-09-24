/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.repository;

import java.io.File;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;


/**
 * A repository used to retrieve files in the domain directory structure.
 *
 * @author John Bailey
 */
public class LocalFileRepository extends LocalDeploymentFileRepository implements HostFileRepository, Service<HostFileRepository> {

    private final File repositoryRoot;
    private final File configurationRoot;

    public LocalFileRepository(final File repositoryRoot, final File deploymentRoot, final File configurationRoot) {
        super(deploymentRoot);
        this.repositoryRoot = repositoryRoot;
        this.configurationRoot = configurationRoot;
    }

    /** {@inheritDoc} */
    @Override
    public File getFile(final String relativePath) {
        return new File(repositoryRoot, relativePath);
    }

    /** {@inheritDoc} */
    @Override
    public File getConfigurationFile(String relativePath) {
        return new File(configurationRoot, relativePath);
    }

    /** {@inheritDoc} */
    @Override
    public File[] getDeploymentFiles(ContentReference reference) {
        return getDeploymentRoot(reference).listFiles();
    }

    /** {@inheritDoc} */
    @Override
    public File getDeploymentRoot(ContentReference reference) {
        if (reference == null || reference.getHexHash().isEmpty()) {
            return deploymentRoot;
        }
        File first = new File(deploymentRoot, reference.getHexHash().substring(0,2));
        return new File(first, reference.getHexHash().substring(2));
    }

    @Override
    public void start(StartContext context) throws StartException {
        // no-op
    }

    @Override
    public void stop(StopContext context) {
        // no-op
    }

    @Override
    public HostFileRepository getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
