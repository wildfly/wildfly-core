/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import java.io.File;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface DeploymentFileRepository {

    /**
     * Get the files associated with a given deployment.
     *
     * @param reference the reference of the deployment content.

     * @return the files associated with the deployment, or <code>null</code> if it is not found
     */
    File[] getDeploymentFiles(final ContentReference reference);

    /**
     * Gets the directory under which files associated with a given deployment
     * would be found.
     *
     * @param reference the reference of the deployment content.

     * @return the directory. Will not be <code>null</code>, even if the
     *         deployment is unknown
     */
    File getDeploymentRoot(ContentReference reference);

    /**
     * Deletes a deployment from the local file system
     *
     * @param reference the reference of the deployment content.
     */
    void deleteDeployment(ContentReference reference);

}
