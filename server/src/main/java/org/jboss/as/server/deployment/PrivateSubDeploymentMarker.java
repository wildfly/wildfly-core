/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

/**
 * Marker for sub deployments that are not globally accessible, such as war's. If a sub deployment does not have this marker set
 * then all other deployments will be given access to it, through a module dependency on sub deployments module.
 *
 * @author Stuart Douglas
 *
 */
public class PrivateSubDeploymentMarker {
    private static final AttachmentKey<Boolean> PRIVATE_SUB_DEPLOYMENT = AttachmentKey.create(Boolean.class);

    public static void mark(DeploymentUnit attachable) {
        attachable.putAttachment(PRIVATE_SUB_DEPLOYMENT, true);
    }

    public static boolean isPrivate(DeploymentUnit resourceRoot) {
        Boolean res = resourceRoot.getAttachment(PRIVATE_SUB_DEPLOYMENT);
        return res != null && res;
    }

    private PrivateSubDeploymentMarker() {

    }
}
