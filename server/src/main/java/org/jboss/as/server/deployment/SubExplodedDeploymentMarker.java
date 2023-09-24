/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.as.server.deployment.module.ResourceRoot;

/**
 * Marker for sub deployments which will be mounted exploded
 *
 * @author Lin Gao
 *
 */
public class SubExplodedDeploymentMarker {
    private static final AttachmentKey<Boolean> SUB_DEPLOYMENT_EXPLODED_MARKER = AttachmentKey.create(Boolean.class);

    public static void mark(ResourceRoot resourceRoot) {
        resourceRoot.putAttachment(SUB_DEPLOYMENT_EXPLODED_MARKER, true);
    }

    public static boolean isSubExplodedResourceRoot(ResourceRoot resourceRoot) {
        Boolean res = resourceRoot.getAttachment(SUB_DEPLOYMENT_EXPLODED_MARKER);
        return res != null && res;
    }

    private SubExplodedDeploymentMarker() {

    }
}
