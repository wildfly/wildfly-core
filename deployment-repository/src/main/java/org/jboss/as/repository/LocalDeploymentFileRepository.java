/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import java.io.File;
import java.io.IOException;
import org.jboss.as.repository.logging.DeploymentRepositoryLogger;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class LocalDeploymentFileRepository implements DeploymentFileRepository {
    final File deploymentRoot;

    public LocalDeploymentFileRepository(final File deploymentRoot) {
        this.deploymentRoot = deploymentRoot;
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
        String hex = reference.getHexHash();
        File first = new File(deploymentRoot, hex.substring(0,2));
        return new File(first, hex.substring(2));
    }

    @Override
    public void deleteDeployment(ContentReference reference) {
        File deployment = getDeploymentRoot(reference);
        if (deployment != deploymentRoot) {
            try {
                PathUtil.deleteRecursively(deployment.toPath());
                if (deployment.getParentFile().list() != null && deployment.getParentFile().list().length == 0) {
                    deployment.getParentFile().delete();
                }
            } catch (IOException ex) {
                DeploymentRepositoryLogger.ROOT_LOGGER.couldNotDeleteDeployment(ex, deployment.getAbsolutePath());
            }
        }
    }
}
