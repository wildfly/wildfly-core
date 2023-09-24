/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt.domain;

import java.io.File;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
interface RemoteFileRepositoryExecutor {
    File getFile(final String relativePath, final byte repoId, final File localDeploymentFolder);
}
