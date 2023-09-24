/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.dependencies;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.server.deployment.AttachmentKey;

/**
 * @author Stuart Douglas
 */
class DeploymentDependencies {

    public static final AttachmentKey<DeploymentDependencies> ATTACHMENT_KEY = AttachmentKey.create(DeploymentDependencies.class);

    private final List<String> dependencies = new ArrayList<String>();

    public List<String> getDependencies() {
        return dependencies;
    }
}
