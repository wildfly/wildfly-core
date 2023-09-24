/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.as.server.deployment.module.ResourceRoot;

/**
 * Marker for sub deployments
 *
 * @author Stuart Douglas
 *
 */
public class SubDeploymentMarker {
    private static final AttachmentKey<Boolean> SUB_DEPLOYMENT_ROOT_MARKER = AttachmentKey.create(Boolean.class);

    public static void mark(ResourceRoot attachable) {
        attachable.putAttachment(SUB_DEPLOYMENT_ROOT_MARKER, true);
    }

    public static boolean isSubDeployment(ResourceRoot resourceRoot) {
        Boolean res = resourceRoot.getAttachment(SUB_DEPLOYMENT_ROOT_MARKER);
        return res != null && res;
    }

    private SubDeploymentMarker() {

    }
}
