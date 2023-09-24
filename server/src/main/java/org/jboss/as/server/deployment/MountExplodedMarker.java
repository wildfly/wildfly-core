/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

/**
 *
 * @author Stuart Douglas
 */
public class MountExplodedMarker {

    private static final AttachmentKey<Boolean> MOUNT_EXPLODED = AttachmentKey.create(Boolean.class);

    public static void setMountExploded(final DeploymentUnit deploymentUnit) {
        deploymentUnit.putAttachment(MOUNT_EXPLODED, true);
    }

    public static boolean isMountExploded(final DeploymentUnit deploymentUnit) {
        Boolean exploded = deploymentUnit.getAttachment(MOUNT_EXPLODED);
        return exploded != null ? exploded : false;
    }
}
