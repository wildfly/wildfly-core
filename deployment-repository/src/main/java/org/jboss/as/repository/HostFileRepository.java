/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.repository;

import java.io.File;


/**
 * A repository used to retrieve files in the domain directory structure.
 *
 * @author John Bailey
 */
public interface HostFileRepository extends DeploymentFileRepository {
    /**
     * Get a file relative to the repository root.
     *
     * @param relativePath Relative path to the file
     * @return The file at that path, or null if it is not found
     */
    File getFile(final String relativePath);


    /**
     * Get a file relative to the configuration root.
     *
     * @param relativePath Relative path to the file
     * @return The file at that path, or null if it is not found
     */
    File getConfigurationFile(final String relativePath);
}
